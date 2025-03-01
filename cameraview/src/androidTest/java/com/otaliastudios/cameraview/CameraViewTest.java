package com.otaliastudios.cameraview;


import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PointF;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.otaliastudios.cameraview.controls.Audio;
import com.otaliastudios.cameraview.controls.ControlParser;
import com.otaliastudios.cameraview.controls.Engine;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.controls.Flash;
import com.otaliastudios.cameraview.controls.Preview;
import com.otaliastudios.cameraview.engine.CameraEngine;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;
import com.otaliastudios.cameraview.controls.Grid;
import com.otaliastudios.cameraview.controls.Hdr;
import com.otaliastudios.cameraview.controls.Mode;
import com.otaliastudios.cameraview.controls.VideoCodec;
import com.otaliastudios.cameraview.controls.WhiteBalance;
import com.otaliastudios.cameraview.gesture.GestureParser;
import com.otaliastudios.cameraview.gesture.PinchGestureFinder;
import com.otaliastudios.cameraview.gesture.ScrollGestureFinder;
import com.otaliastudios.cameraview.gesture.TapGestureFinder;
import com.otaliastudios.cameraview.engine.MockCameraEngine;
import com.otaliastudios.cameraview.internal.utils.Op;
import com.otaliastudios.cameraview.markers.AutoFocusMarker;
import com.otaliastudios.cameraview.markers.DefaultAutoFocusMarker;
import com.otaliastudios.cameraview.markers.MarkerLayout;
import com.otaliastudios.cameraview.preview.MockCameraPreview;
import com.otaliastudios.cameraview.preview.CameraPreview;
import com.otaliastudios.cameraview.size.Size;
import com.otaliastudios.cameraview.size.SizeSelector;
import com.otaliastudios.cameraview.size.SizeSelectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

import static android.view.View.MeasureSpec.*;
import static android.view.ViewGroup.LayoutParams.*;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CameraViewTest extends BaseTest {

    private CameraView cameraView;
    private MockCameraEngine mockController;
    private CameraPreview mockPreview;
    private boolean hasPermissions;

    @Before
    public void setUp() {
        ui(new Runnable() {
            @Override
            public void run() {
                Context context = context();
                cameraView = new CameraView(context) {

                    @NonNull
                    @Override
                    protected CameraEngine instantiateCameraEngine(@NonNull Engine engine, @NonNull CameraEngine.Callback callback) {
                        mockController = spy(new MockCameraEngine(callback));
                        return mockController;
                    }

                    @NonNull
                    @Override
                    protected CameraPreview instantiatePreview(@NonNull Preview preview, @NonNull Context context, @NonNull ViewGroup container) {
                        mockPreview = spy(new MockCameraPreview(context, container));
                        return mockPreview;
                    }

                    @Override
                    protected boolean checkPermissions(@NonNull Audio audio) {
                        return hasPermissions;
                    }
                };
                // Instantiate preview now.
                cameraView.doInstantiatePreview();
            }
        });
    }

    @After
    public void tearDown() {
        cameraView = null;
        mockController = null;
        mockPreview = null;
        hasPermissions = false;
    }

    //region testLifecycle

    @Test
    public void testOpen() {
        cameraView.open();
        verify(mockPreview, times(1)).onResume();
        // Can't verify controller, depends on permissions.
        // See to-do at the end.
    }

    @Test
    public void testClose() {
        cameraView.close();
        verify(mockPreview, times(1)).onPause();
        verify(mockController, times(1)).stop();
    }

    @Test
    public void testDestroy() {
        cameraView.destroy();
        verify(mockPreview, times(1)).onDestroy();
        verify(mockController, times(1)).destroy();
    }

    //region testDefaults

    @Test
    public void testNullBeforeStart() {
        assertFalse(cameraView.isOpened());
        assertNull(cameraView.getCameraOptions());
        assertNull(cameraView.getSnapshotSize());
        assertNull(cameraView.getPictureSize());
        assertNull(cameraView.getVideoSize());
    }

    @Test
    public void testDefaults() {
        // CameraEngine
        TypedArray empty = context().obtainStyledAttributes(new int[]{});
        ControlParser controls = new ControlParser(context(), empty);
        assertEquals(cameraView.getFlash(), controls.getFlash());
        assertEquals(cameraView.getFacing(), controls.getFacing());
        assertEquals(cameraView.getGrid(), controls.getGrid());
        assertEquals(cameraView.getWhiteBalance(), controls.getWhiteBalance());
        assertEquals(cameraView.getMode(), controls.getMode());
        assertEquals(cameraView.getHdr(), controls.getHdr());
        assertEquals(cameraView.getAudio(), controls.getAudio());
        assertEquals(cameraView.getVideoCodec(), controls.getVideoCodec());
        //noinspection SimplifiableJUnitAssertion
        assertEquals(cameraView.getLocation(), null);
        assertEquals(cameraView.getExposureCorrection(), 0f, 0f);
        assertEquals(cameraView.getZoom(), 0f, 0f);
        assertEquals(cameraView.getVideoMaxDuration(), 0, 0);
        assertEquals(cameraView.getVideoMaxSize(), 0, 0);

        // Self managed
        GestureParser gestures = new GestureParser(empty);
        assertEquals(cameraView.getPlaySounds(), CameraView.DEFAULT_PLAY_SOUNDS);
        assertEquals(cameraView.getUseDeviceOrientation(), CameraView.DEFAULT_USE_DEVICE_ORIENTATION);
        assertEquals(cameraView.getGestureAction(Gesture.TAP), gestures.getTapAction());
        assertEquals(cameraView.getGestureAction(Gesture.LONG_TAP), gestures.getLongTapAction());
        assertEquals(cameraView.getGestureAction(Gesture.PINCH), gestures.getPinchAction());
        assertEquals(cameraView.getGestureAction(Gesture.SCROLL_HORIZONTAL), gestures.getHorizontalScrollAction());
        assertEquals(cameraView.getGestureAction(Gesture.SCROLL_VERTICAL), gestures.getVerticalScrollAction());
    }

    //endregion

    //region testGesture

    @Test
    public void testGesture_mapAndClear() {
        // Assignable
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        assertEquals(cameraView.getGestureAction(Gesture.PINCH), GestureAction.ZOOM);

        // Not assignable: This is like clearing
        cameraView.mapGesture(Gesture.PINCH, GestureAction.TAKE_PICTURE);
        assertEquals(cameraView.getGestureAction(Gesture.PINCH), GestureAction.NONE);

        // Test clearing
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        cameraView.clearGesture(Gesture.PINCH);
        assertEquals(cameraView.getGestureAction(Gesture.PINCH), GestureAction.NONE);
    }

    @Test
    public void testGesture_enablingDisablingLayouts() {
        cameraView.clearGesture(Gesture.TAP);
        cameraView.clearGesture(Gesture.LONG_TAP);
        cameraView.clearGesture(Gesture.PINCH);
        cameraView.clearGesture(Gesture.SCROLL_HORIZONTAL);
        cameraView.clearGesture(Gesture.SCROLL_VERTICAL);

        // PinchGestureLayout
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);
        assertTrue(cameraView.mPinchGestureFinder.isActive());
        cameraView.clearGesture(Gesture.PINCH);
        assertFalse(cameraView.mPinchGestureFinder.isActive());

        // TapGestureLayout
        cameraView.mapGesture(Gesture.TAP, GestureAction.TAKE_PICTURE);
        assertTrue(cameraView.mTapGestureFinder.isActive());
        cameraView.clearGesture(Gesture.TAP);
        assertFalse(cameraView.mPinchGestureFinder.isActive());

        // ScrollGestureLayout
        cameraView.mapGesture(Gesture.SCROLL_HORIZONTAL, GestureAction.ZOOM);
        assertTrue(cameraView.mScrollGestureFinder.isActive());
        cameraView.clearGesture(Gesture.SCROLL_HORIZONTAL);
        assertFalse(cameraView.mScrollGestureFinder.isActive());
    }

    //endregion

    //region testGestureAction

    @Test
    public void testGestureAction_capture() {
        CameraOptions o = mock(CameraOptions.class);
        mockController.setMockCameraOptions(o);
        mockController.setMockEngineState(true);
        MotionEvent event = MotionEvent.obtain(0L, 0L, 0, 0f, 0f, 0);
        ui(new Runnable() {
            @Override
            public void run() {
                cameraView.mTapGestureFinder = new TapGestureFinder(cameraView.mCameraCallbacks) {
                    protected boolean handleTouchEvent(@NonNull MotionEvent event) {
                        setGesture(Gesture.TAP);
                        return true;
                    }
                };
            }
        });
        cameraView.mapGesture(Gesture.TAP, GestureAction.TAKE_PICTURE);
        cameraView.dispatchTouchEvent(event);
        assertTrue(mockController.mPictureCaptured);
    }

    @Test
    public void testGestureAction_focus() {
        CameraOptions o = mock(CameraOptions.class);
        mockController.setMockCameraOptions(o);
        mockController.setMockEngineState(true);
        MotionEvent event = MotionEvent.obtain(0L, 0L, 0, 0f, 0f, 0);
        ui(new Runnable() {
            @Override
            public void run() {
                cameraView.mTapGestureFinder = new TapGestureFinder(cameraView.mCameraCallbacks) {
                    protected boolean handleTouchEvent(@NonNull MotionEvent event) {
                        setGesture(Gesture.TAP);
                        return true;
                    }
                };
            }
        });
        mockController.mFocusStarted = false;
        cameraView.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS);
        cameraView.dispatchTouchEvent(event);
        assertTrue(mockController.mFocusStarted);
    }

    private class FactorHolder { float value; }

    @Test
    public void testGestureAction_zoom() {
        CameraOptions o = mock(CameraOptions.class);
        mockController.setMockCameraOptions(o);
        mockController.setMockEngineState(true);
        mockController.mZoomChanged = false;
        MotionEvent event = MotionEvent.obtain(0L, 0L, 0, 0f, 0f, 0);
        final FactorHolder factor = new FactorHolder();
        ui(new Runnable() {
            @Override
            public void run() {
                cameraView.mPinchGestureFinder = new PinchGestureFinder(cameraView.mCameraCallbacks) {
                    @Override
                    protected boolean handleTouchEvent(@NonNull MotionEvent event) {
                        setGesture(Gesture.PINCH);
                        return true;
                    }

                    @Override
                    protected float getFactor() {
                        return factor.value;
                    }
                };
                cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM);

            }
        });

        // If factor is 0, we return the same value. The controller should not be notified.
        factor.value = 0f;
        cameraView.dispatchTouchEvent(event);
        assertFalse(mockController.mZoomChanged);

        // For larger factors, the value is scaled. The controller should be notified.
        factor.value = 1f;
        cameraView.dispatchTouchEvent(event);
        assertTrue(mockController.mZoomChanged);
    }

    @Test
    public void testGestureAction_exposureCorrection() {
        CameraOptions o = mock(CameraOptions.class);
        when(o.getExposureCorrectionMinValue()).thenReturn(-10f);
        when(o.getExposureCorrectionMaxValue()).thenReturn(10f);
        mockController.setMockCameraOptions(o);
        mockController.setMockEngineState(true);
        mockController.mExposureCorrectionChanged = false;
        MotionEvent event = MotionEvent.obtain(0L, 0L, 0, 0f, 0f, 0);
        final FactorHolder factor = new FactorHolder();
        ui(new Runnable() {
            @Override
            public void run() {
                cameraView.mScrollGestureFinder = new ScrollGestureFinder(cameraView.mCameraCallbacks) {
                    @Override
                    protected boolean handleTouchEvent(@NonNull MotionEvent event) {
                        setGesture(Gesture.SCROLL_HORIZONTAL);
                        return true;
                    }

                    @Override
                    protected float getFactor() {
                        return factor.value;
                    }
                };
                cameraView.mapGesture(Gesture.SCROLL_HORIZONTAL, GestureAction.EXPOSURE_CORRECTION);
            }
        });

        // If factor is 0, we return the same value. The controller should not be notified.
        factor.value = 0f;
        cameraView.dispatchTouchEvent(event);
        assertFalse(mockController.mExposureCorrectionChanged);

        // For larger factors, the value is scaled. The controller should be notified.
        factor.value = 1f;
        cameraView.dispatchTouchEvent(event);
        assertTrue(mockController.mExposureCorrectionChanged);
    }

    //endregion

    //region testMeasure

    private void mockPreviewStreamSize() {
        Size size = new Size(900, 1600);
        mockController.setMockPreviewStreamSize(size);
    }

    @Test
    public void testMeasure_early() {
        mockController.setMockPreviewStreamSize(null);
        cameraView.measure(
                makeMeasureSpec(500, EXACTLY),
                makeMeasureSpec(500, EXACTLY));
        assertEquals(cameraView.getMeasuredWidth(), 500);
        assertEquals(cameraView.getMeasuredHeight(), 500);
    }

    @Test
    public void testMeasure_matchParentBoth() {
        mockPreviewStreamSize();

        // Respect parent/layout constraints on both dimensions.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        cameraView.measure(
                makeMeasureSpec(500, EXACTLY),
                makeMeasureSpec(500, EXACTLY));
        assertEquals(cameraView.getMeasuredWidth(), 500);
        assertEquals(cameraView.getMeasuredHeight(), 500);

        // Even if the parent ViewGroup passes AT_MOST
        cameraView.measure(
                makeMeasureSpec(500, AT_MOST),
                makeMeasureSpec(500, AT_MOST));
        assertEquals(cameraView.getMeasuredWidth(), 500);
        assertEquals(cameraView.getMeasuredHeight(), 500);

        cameraView.measure(
                makeMeasureSpec(500, EXACTLY),
                makeMeasureSpec(500, AT_MOST));
        assertEquals(cameraView.getMeasuredWidth(), 500);
        assertEquals(cameraView.getMeasuredHeight(), 500);
    }

    @Test
    public void testMeasure_wrapContentBoth() {
        mockPreviewStreamSize();

        // Respect parent constraints, but fit aspect ratio.
        // Fit into a 160x160 parent so we espect final width to be 90.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        cameraView.measure(
                makeMeasureSpec(160, AT_MOST),
                makeMeasureSpec(160, AT_MOST));
        assertEquals(cameraView.getMeasuredWidth(), 90);
        assertEquals(cameraView.getMeasuredHeight(), 160);
    }

    @Test
    public void testMeasure_wrapContentSingle() {
        mockPreviewStreamSize();

        // Respect MATCH_PARENT on height, change width to fit the aspect ratio.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        cameraView.measure(
                makeMeasureSpec(160, AT_MOST),
                makeMeasureSpec(160, AT_MOST));
        assertEquals(cameraView.getMeasuredWidth(), 90);
        assertEquals(cameraView.getMeasuredHeight(), 160);

        // Respect MATCH_PARENT on width. Enlarge height trying to fit aspect ratio as much as possible.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        cameraView.measure(
                makeMeasureSpec(160, AT_MOST),
                makeMeasureSpec(160, AT_MOST));
        assertEquals(cameraView.getMeasuredWidth(), 160);
        assertEquals(cameraView.getMeasuredHeight(), 160);
    }

    @Test
    public void testMeasure_scrollableContainer() {
        mockPreviewStreamSize();

        // Assume a vertical scroll view. It will pass UNSPECIFIED as height.
        // We respect MATCH_PARENT on width (160), and enlarge height to match the aspect ratio.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        cameraView.measure(
                makeMeasureSpec(160, AT_MOST),
                makeMeasureSpec(0, UNSPECIFIED));
        assertEquals(cameraView.getMeasuredWidth(), 160);
        assertEquals(cameraView.getMeasuredHeight(), 160f * (16f / 9f), 1f); // Leave a margin

        // Assume a view scrolling in both dimensions. It will pass UNSPECIFIED.
        // In this case we must fit the exact preview dimension.
        cameraView.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        cameraView.measure(
                makeMeasureSpec(0, UNSPECIFIED),
                makeMeasureSpec(0, UNSPECIFIED));
        assertEquals(cameraView.getMeasuredWidth(), 900);
        assertEquals(cameraView.getMeasuredHeight(), 1600);
    }

    //endregion

    //region Zoom, ExposureCorrection

    @Test
    public void testZoom() {
        cameraView.setZoom(0.5f);
        assertEquals(cameraView.getZoom(), 0.5f, 0f);
        cameraView.setZoom(-10f);
        assertEquals(cameraView.getZoom(), 0f, 0f);
        cameraView.setZoom(10f);
        assertEquals(cameraView.getZoom(), 1f, 0f);
    }

    @Test
    public void testExposureCorrection() {
        // This needs a valid CameraOptions value.
        CameraOptions o = mock(CameraOptions.class);
        when(o.getExposureCorrectionMinValue()).thenReturn(-10f);
        when(o.getExposureCorrectionMaxValue()).thenReturn(10f);
        mockController.setMockCameraOptions(o);

        cameraView.setExposureCorrection(5f);
        assertEquals(cameraView.getExposureCorrection(), 5f, 0f);
        cameraView.setExposureCorrection(-100f);
        assertEquals(cameraView.getExposureCorrection(), -10f, 0f);
        cameraView.setExposureCorrection(100f);
        assertEquals(cameraView.getExposureCorrection(), 10f, 0f);
    }

    //endregion

    //region testLocation

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testSetLocation() {
        cameraView.setLocation(50d, -50d);
        assertEquals(50d, mockController.getLocation().getLatitude(), 0);
        assertEquals(-50d, mockController.getLocation().getLongitude(), 0);
        assertEquals(0, mockController.getLocation().getAltitude(), 0);
        assertEquals("Unknown", mockController.getLocation().getProvider());
        assertEquals(System.currentTimeMillis(), mockController.getLocation().getTime(), 1000f);

        Location source = new Location("Provider");
        source.setTime(5000);
        source.setLatitude(10d);
        source.setLongitude(-10d);
        source.setAltitude(50d);
        cameraView.setLocation(source);
        Location other = cameraView.getLocation();
        assertEquals(10d, other.getLatitude(), 0d);
        assertEquals(-10d, other.getLongitude(), 0d);
        assertEquals(50d, other.getAltitude(), 0d);
        assertEquals("Provider", other.getProvider());
        assertEquals(5000, other.getTime());
    }

    //endregion

    //region test autofocus

    @Test(expected = IllegalArgumentException.class)
    public void testStartAutoFocus_illegal() {
        cameraView.startAutoFocus(-1, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testStartAutoFocus_illegal2() {
        cameraView.setLeft(0);
        cameraView.setRight(100);
        cameraView.setTop(0);
        cameraView.setBottom(100);
        cameraView.startAutoFocus(200, 200);
    }

    @Test
    public void testStartAutoFocus() {
        cameraView.setLeft(0);
        cameraView.setRight(100);
        cameraView.setTop(0);
        cameraView.setBottom(100);
        cameraView.startAutoFocus(50, 50);
        assertTrue(mockController.mFocusStarted);
    }

    //endregion

    //region test setParameters

    @Test
    public void testSetPlaySounds() {
        cameraView.setPlaySounds(true);
        assertTrue(cameraView.getPlaySounds());
        cameraView.setPlaySounds(false);
        assertFalse(cameraView.getPlaySounds());
    }

    @Test
    public void testSetUseDeviceOrientation() {
        cameraView.setUseDeviceOrientation(true);
        assertTrue(cameraView.getUseDeviceOrientation());
        cameraView.setUseDeviceOrientation(false);
        assertFalse(cameraView.getUseDeviceOrientation());
    }

    @Test
    public void testSetFlash() {
        cameraView.set(Flash.TORCH);
        assertEquals(cameraView.get(Flash.class), Flash.TORCH);
        cameraView.set(Flash.OFF);
        assertEquals(cameraView.get(Flash.class), Flash.OFF);
    }

    @Test
    public void testSetFacing() {
        cameraView.set(Facing.FRONT);
        assertEquals(cameraView.get(Facing.class), Facing.FRONT);
        cameraView.set(Facing.BACK);
        assertEquals(cameraView.get(Facing.class), Facing.BACK);
    }

    @Test
    public void testToggleFacing() {
        cameraView.set(Facing.FRONT);
        cameraView.toggleFacing();
        assertEquals(cameraView.get(Facing.class), Facing.BACK);
        cameraView.toggleFacing();
        assertEquals(cameraView.get(Facing.class), Facing.FRONT);
    }

    @Test
    public void testSetGrid() {
        cameraView.set(Grid.DRAW_3X3);
        assertEquals(cameraView.get(Grid.class), Grid.DRAW_3X3);
        cameraView.set(Grid.OFF);
        assertEquals(cameraView.get(Grid.class), Grid.OFF);
    }

    @Test
    public void testSetWhiteBalance() {
        cameraView.set(WhiteBalance.CLOUDY);
        assertEquals(cameraView.get(WhiteBalance.class), WhiteBalance.CLOUDY);
        cameraView.set(WhiteBalance.AUTO);
        assertEquals(cameraView.get(WhiteBalance.class), WhiteBalance.AUTO);
    }

    @Test
    public void testMode() {
        cameraView.set(Mode.VIDEO);
        assertEquals(cameraView.get(Mode.class), Mode.VIDEO);
        cameraView.set(Mode.PICTURE);
        assertEquals(cameraView.get(Mode.class), Mode.PICTURE);
    }

    @Test
    public void testHdr() {
        cameraView.set(Hdr.ON);
        assertEquals(cameraView.get(Hdr.class), Hdr.ON);
        cameraView.set(Hdr.OFF);
        assertEquals(cameraView.get(Hdr.class), Hdr.OFF);
    }

    @Test
    public void testAudio() {
        cameraView.set(Audio.ON);
        assertEquals(cameraView.get(Audio.class), Audio.ON);
        cameraView.set(Audio.OFF);
        assertEquals(cameraView.get(Audio.class), Audio.OFF);
    }

    @Test
    public void testVideoCodec() {
        cameraView.set(VideoCodec.H_263);
        assertEquals(cameraView.get(VideoCodec.class), VideoCodec.H_263);
        cameraView.set(VideoCodec.H_264);
        assertEquals(cameraView.get(VideoCodec.class), VideoCodec.H_264);
    }

    @Test
    public void testPreviewStreamSizeSelector() {
        SizeSelector source = SizeSelectors.minHeight(50);
        cameraView.setPreviewStreamSize(source);
        SizeSelector result = mockController.getPreviewStreamSizeSelector();
        assertNotNull(result);
        assertEquals(result, source);
    }

    @Test
    public void testPictureSizeSelector() {
        SizeSelector source = SizeSelectors.minHeight(50);
        cameraView.setPictureSize(source);
        SizeSelector result = mockController.getPictureSizeSelector();
        assertNotNull(result);
        assertEquals(result, source);
    }

    @Test
    public void testVideoSizeSelector() {
        SizeSelector source = SizeSelectors.minHeight(50);
        cameraView.setVideoSize(source);
        SizeSelector result = mockController.getVideoSizeSelector();
        assertNotNull(result);
        assertEquals(result, source);
    }

    @Test
    public void testVideoMaxSize() {
        cameraView.setVideoMaxSize(5000);
        assertEquals(cameraView.getVideoMaxSize(), 5000);
    }

    @Test
    public void testVideoMaxDuration() {
        cameraView.setVideoMaxDuration(5000);
        assertEquals(cameraView.getVideoMaxDuration(), 5000);
    }

    //endregion

    //region Lists of listeners and processors

    @SuppressWarnings("UseBulkOperation")
    @Test
    public void testCameraListenerList() {
        assertTrue(cameraView.mListeners.isEmpty());

        CameraListener listener = new CameraListener() {};
        cameraView.addCameraListener(listener);
        assertEquals(cameraView.mListeners.size(), 1);

        cameraView.removeCameraListener(listener);
        assertEquals(cameraView.mListeners.size(), 0);

        cameraView.addCameraListener(listener);
        cameraView.addCameraListener(listener);
        assertEquals(cameraView.mListeners.size(), 2);

        cameraView.clearCameraListeners();
        assertTrue(cameraView.mListeners.isEmpty());

        // Ensure this does not throw a ConcurrentModificationException
        cameraView.addCameraListener(new CameraListener() {});
        cameraView.addCameraListener(new CameraListener() {});
        cameraView.addCameraListener(new CameraListener() {});
        for (CameraListener test : cameraView.mListeners) {
            cameraView.mListeners.remove(test);
        }
    }

    @SuppressWarnings({"NullableProblems", "UseBulkOperation"})
    @Test
    public void testFrameProcessorsList() {
        assertTrue(cameraView.mFrameProcessors.isEmpty());

        FrameProcessor processor = new FrameProcessor() {
            public void process(@NonNull Frame frame) {}
        };
        cameraView.addFrameProcessor(processor);
        assertEquals(cameraView.mFrameProcessors.size(), 1);

        cameraView.removeFrameProcessor(processor);
        assertEquals(cameraView.mFrameProcessors.size(), 0);

        cameraView.addFrameProcessor(processor);
        cameraView.addFrameProcessor(processor);
        assertEquals(cameraView.mFrameProcessors.size(), 2);

        cameraView.clearFrameProcessors();
        assertTrue(cameraView.mFrameProcessors.isEmpty());

        // Ensure this does not throw a ConcurrentModificationException
        cameraView.addFrameProcessor(new FrameProcessor() { public void process(@NonNull Frame f) {} });
        cameraView.addFrameProcessor(new FrameProcessor() { public void process(@NonNull Frame f) {} });
        cameraView.addFrameProcessor(new FrameProcessor() { public void process(@NonNull Frame f) {} });
        for (FrameProcessor test : cameraView.mFrameProcessors) {
            cameraView.mFrameProcessors.remove(test);
        }
    }

    //endregion

    //region Snapshots

    @Test
    public void testSetSnapshotMaxSize() {
        cameraView.setSnapshotMaxWidth(500);
        cameraView.setSnapshotMaxHeight(1000);
        assertEquals(mockController.getSnapshotMaxWidth(), 500);
        assertEquals(mockController.getSnapshotMaxHeight(), 1000);
    }

    //endregion

    //region MarkerLayout

    @Test
    public void testMarkerLayout_forAutoFocus_onMarker() {
        MarkerLayout markerLayout = mock(MarkerLayout.class);
        AutoFocusMarker marker = new DefaultAutoFocusMarker();
        cameraView.mMarkerLayout = markerLayout;
        cameraView.setAutoFocusMarker(marker);
        verify(markerLayout, times(1)).onMarker(MarkerLayout.TYPE_AUTOFOCUS, marker);
    }

    @Test
    public void testMarkerLayout_forAutoFocus_onEvent() {
        MarkerLayout markerLayout = spy(cameraView.mMarkerLayout);
        cameraView.mMarkerLayout = markerLayout;
        final PointF point = new PointF(0, 0);
        final PointF[] points = new PointF[]{ point };
        final Op<Boolean> op = new Op<>(true);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                op.end(true);
                return null;
            }
        }).when(markerLayout).onEvent(MarkerLayout.TYPE_AUTOFOCUS, points);
        cameraView.mCameraCallbacks.dispatchOnFocusStart(Gesture.TAP, point);
        assertNotNull(op.await(100));
    }

    //endregion

    // TODO: test permissions
}
