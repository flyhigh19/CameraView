package com.otaliastudios.cameraview.picture;

import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.view.Surface;

import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraLogger;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.internal.utils.ExifHelper;
import com.otaliastudios.cameraview.internal.utils.WorkerHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

/**
 * A {@link PictureResult} that uses standard APIs.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class Full2PictureRecorder extends PictureRecorder implements ImageReader.OnImageAvailableListener {

    private static final String TAG = Full2PictureRecorder.class.getSimpleName();
    private static final CameraLogger LOG = CameraLogger.create(TAG);

    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING_FOCUS_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE_START = 2;
    private static final int STATE_WAITING_PRECAPTURE_END = 3;
    private static final int STATE_WAITING_CAPTURE = 4;
    private static final int STATE_WAITING_IMAGE = 5;

    private static final int REQUEST_TAG = CameraDevice.TEMPLATE_STILL_CAPTURE;

    private CameraCaptureSession mSession;
    private CaptureRequest.Builder mBuilder;
    private CameraCaptureSession.CaptureCallback mCallback;
    private ImageReader mPictureReader;
    private CaptureRequest.Builder mPictureBuilder;
    private boolean mStopPreviewBeforeCapture;
    private int mState = STATE_IDLE;

    public Full2PictureRecorder(@NonNull PictureResult.Stub stub,
                                @Nullable PictureResultListener listener,
                                @NonNull CameraCaptureSession session,
                                @NonNull CaptureRequest.Builder builder,
                                @NonNull CameraCaptureSession.CaptureCallback callback,
                                @NonNull CaptureRequest.Builder pictureBuilder,
                                @NonNull ImageReader pictureReader,
                                boolean stopPreviewBeforeCapture) {
        super(stub, listener);
        mSession = session;
        mBuilder = builder;
        mCallback = callback;
        mPictureBuilder = pictureBuilder;
        mStopPreviewBeforeCapture = stopPreviewBeforeCapture;
        mPictureReader = pictureReader;
        mPictureReader.setOnImageAvailableListener(this, WorkerHandler.get().getHandler());
    }

    @Override
    public void take() {
        runFocusLock();
    }

    private void runFocusLock() {
        try {
            mState = STATE_WAITING_FOCUS_LOCK;
            mBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
            mSession.capture(mBuilder.build(), mCallback, null);
        } catch (CameraAccessException e) {
            mResult = null;
            mError = e;
            dispatchResult();
        }
    }

    private void runPrecapture() {
        try {
            mState = STATE_WAITING_PRECAPTURE_START;
            mBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mSession.capture(mBuilder.build(), mCallback, null);
        } catch (CameraAccessException e) {
            mResult = null;
            mError = e;
            dispatchResult();
        }
    }

    private void runCapture() {
        try {
            mPictureBuilder.setTag(REQUEST_TAG);
            mPictureBuilder.addTarget(mPictureReader.getSurface());
            mPictureBuilder.set(CaptureRequest.JPEG_ORIENTATION, mResult.rotation);
            if (mStopPreviewBeforeCapture) {
                // These two are present in official samples and are probably meant to speed things up?
                // But from my tests, they actually make everything slower. So this is disabled by default
                // with a boolean coming from the engine. Maybe in the future we can make this configurable
                // as some people might want to stop the preview while picture is being taken even if it
                // increases the latency.
                mSession.stopRepeating();
                mSession.abortCaptures();
            }
            mSession.capture(mPictureBuilder.build(), mCallback, null);
        } catch (CameraAccessException e) {
            mResult = null;
            mError = e;
            dispatchResult();
        }
    }

    public void onCaptureStarted(@NonNull CaptureRequest request) {
        if (request.getTag() == (Integer) REQUEST_TAG) {
            dispatchOnShutter(false);
        }
    }

    public void onCaptureProgressed(@NonNull CaptureResult result) {
        process(result);
    }

    public void onCaptureCompleted(@NonNull CaptureResult result) {
        process(result);
    }

    private void process(@NonNull CaptureResult result) {
        switch (mState) {
            case STATE_IDLE: break;
            case STATE_WAITING_FOCUS_LOCK: {
                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                if (afState == null) {
                    runCapture();
                } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                        || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        mState = STATE_WAITING_CAPTURE;
                        runCapture();
                    } else {
                        runPrecapture();
                    }
                }
                break;
            }
            case STATE_WAITING_PRECAPTURE_START: {
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                    mState = STATE_WAITING_PRECAPTURE_END;
                }
                break;
            }
            case STATE_WAITING_PRECAPTURE_END: {
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null
                        || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    mState = STATE_WAITING_CAPTURE;
                    runCapture();
                }
                break;
            }
            case STATE_WAITING_CAPTURE: {
                if (result instanceof TotalCaptureResult
                        && result.getRequest().getTag() == (Integer) REQUEST_TAG) {
                    mState = STATE_WAITING_IMAGE;
                }
                break;
            }
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        mState = STATE_IDLE;

        // Read the JPEG.
        Image image = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            mResult.data = bytes;
        } catch (Exception e) {
            mResult = null;
            mError = e;
            dispatchResult();
            return;
        } finally {
            if (image != null) image.close();
        }

        // Just like Camera1, unfortunately, the camera might rotate the image
        // and put EXIF=0 instead of respecting our EXIF and leave the image unaltered.
        mResult.format = PictureResult.FORMAT_JPEG;
        mResult.rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(mResult.data));
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            mResult.rotation = ExifHelper.readExifOrientation(exifOrientation);
        } catch (IOException ignore) { }

        // Before leaving, unlock focus.
        try {
            mBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            mSession.capture(mBuilder.build(), mCallback, null);
        } catch (CameraAccessException ignore) { }

        // Leave.
        dispatchResult();
    }


    @Override
    protected void dispatchResult() {
        mState = STATE_IDLE;
        super.dispatchResult();
    }
}
