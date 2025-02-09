package com.otaliastudios.cameraview.demo;

import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import android.view.View;
import android.view.ViewGroup;

import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.gesture.Gesture;
import com.otaliastudios.cameraview.gesture.GestureAction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Controls that we want to display in a ControlView.
 */
public abstract class Option<T> {

    public static List<Option<?>> getAll() {
        return Arrays.asList(
                // Layout
                new Width(false),
                new Height(true),

                // Engine and preview
                new Mode(false),
                new Engine(false),
                new Preview(true),

                // Some controls
                new Flash(false),
                new WhiteBalance(false),
                new Hdr(true),

                // Video recording
                new VideoCodec(false),
                new Audio(true),
                // TODO audio bitRate
                // TODO video bitRate
                // They are a bit annoying because it's not clear what the default should be.

                // Gestures
                new Pinch(false),
                new HorizontalScroll(false),
                new VerticalScroll(false),
                new Tap(false),
                new LongTap(true),

                // Other
                new Grid(false),
                new GridColor(false),
                new UseDeviceOrientation(true)
        );
    }

    private String name;
    private boolean hasDividerBelow;

    private Option(@NonNull String name, boolean hasDividerBelow) {
        this.name = name;
        this.hasDividerBelow = hasDividerBelow;
    }

    @SuppressWarnings("WeakerAccess")
    @NonNull
    public final String getName() {
        return name;
    }

    @SuppressWarnings("WeakerAccess")
    public final boolean hasDividerBelow() {
        return hasDividerBelow;
    }

    @NonNull
    public abstract T get(@NonNull CameraView view);

    @NonNull
    public abstract Collection<T> getAll(@NonNull CameraView view, @NonNull CameraOptions options);

    public abstract void set(@NonNull CameraView view, @NonNull T value);

    @NonNull
    public String toString(@NonNull T value) {
        return String.valueOf(value).replace("_", " ").toLowerCase();
    }

    public static class Width extends Option<Integer> {
        Width(boolean hasDividerBelow) {
            super("Width", hasDividerBelow);
        }

        @NonNull
        @Override
        public Collection<Integer> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            View root = (View) view.getParent();
            ArrayList<Integer> list = new ArrayList<>();
            int boundary = root.getWidth();
            if (boundary == 0) boundary = 1000;
            int step = boundary / 10;
            list.add(ViewGroup.LayoutParams.WRAP_CONTENT);
            list.add(ViewGroup.LayoutParams.MATCH_PARENT);
            for (int i = step; i < boundary; i += step) {
                list.add(i);
            }
            return list;
        }

        @NonNull
        @Override
        public Integer get(@NonNull CameraView view) {
            return view.getLayoutParams().width;
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull Integer value) {
            view.getLayoutParams().width = (int) value;
            view.setLayoutParams(view.getLayoutParams());
        }

        @NonNull
        @Override
        public String toString(@NonNull Integer value) {
            if (value == ViewGroup.LayoutParams.MATCH_PARENT) return "match parent";
            if (value == ViewGroup.LayoutParams.WRAP_CONTENT) return "wrap content";
            return super.toString(value);
        }
    }

    public static class Height extends Option<Integer> {
        Height(boolean hasDividerBelow) {
            super("Height", hasDividerBelow);
        }

        @NonNull
        @Override
        public Collection<Integer> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            View root = (View) view.getParent();
            ArrayList<Integer> list = new ArrayList<>();
            int boundary = root.getHeight();
            if (boundary == 0) boundary = 1000;
            int step = boundary / 10;
            list.add(ViewGroup.LayoutParams.WRAP_CONTENT);
            list.add(ViewGroup.LayoutParams.MATCH_PARENT);
            for (int i = step; i < boundary; i += step) {
                list.add(i);
            }
            return list;
        }

        @NonNull
        @Override
        public Integer get(@NonNull CameraView view) {
            return view.getLayoutParams().height;
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull Integer value) {
            view.getLayoutParams().height = (int) value;
            view.setLayoutParams(view.getLayoutParams());
        }

        @NonNull
        @Override
        public String toString(@NonNull Integer value) {
            if (value == ViewGroup.LayoutParams.MATCH_PARENT) return "match parent";
            if (value == ViewGroup.LayoutParams.WRAP_CONTENT) return "wrap content";
            return super.toString(value);
        }
    }

    private static abstract class ControlOption<T extends com.otaliastudios.cameraview.controls.Control> extends Option<T> {
        private final Class<T> controlClass;

        ControlOption(@NonNull Class<T> controlClass, String name, boolean hasDividerBelow) {
            super(name, hasDividerBelow);
            this.controlClass = controlClass;
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull T value) {
            view.set(value);
        }

        @NonNull
        @Override
        public T get(@NonNull CameraView view) {
            return view.get(controlClass);
        }

        @NonNull
        @Override
        public Collection<T> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            return options.getSupportedControls(controlClass);
        }
    }

    public static class Mode extends ControlOption<com.otaliastudios.cameraview.controls.Mode> {
        Mode(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Mode.class, "Mode", hasDividerBelow);
        }
    }

    public static class Engine extends ControlOption<com.otaliastudios.cameraview.controls.Engine> {
        Engine(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Engine.class, "Engine", hasDividerBelow);
        }

        @Override
        public void set(final @NonNull CameraView view, final @NonNull com.otaliastudios.cameraview.controls.Engine value) {
            boolean started = view.isOpened();
            if (started) {
                view.addCameraListener(new CameraListener() {
                    @Override
                    public void onCameraClosed() {
                        super.onCameraClosed();
                        view.removeCameraListener(this);
                        view.setEngine(value);
                        view.open();
                    }
                });
                view.close();
            } else {
                view.setEngine(value);
            }
        }
    }

    public static class Preview extends ControlOption<com.otaliastudios.cameraview.controls.Preview> {
        Preview(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Preview.class, "Preview Surface", hasDividerBelow);
        }

        @Override
        public void set(final @NonNull CameraView view, final @NonNull com.otaliastudios.cameraview.controls.Preview value) {
            boolean opened = view.isOpened();
            if (opened) {
                view.addCameraListener(new CameraListener() {
                    @Override
                    public void onCameraClosed() {
                        super.onCameraClosed();
                        view.removeCameraListener(this);
                        applyPreview(view, value);
                        view.open();
                    }
                });
                view.close();
            } else {
                applyPreview(view, value);
            }
        }

        // This is really tricky since the preview can only be changed when not attached to window.
        private void applyPreview(@NonNull CameraView cameraView,
                                  @NonNull com.otaliastudios.cameraview.controls.Preview newPreview) {
            ViewGroup.LayoutParams params = cameraView.getLayoutParams();
            ViewGroup parent = (ViewGroup) cameraView.getParent();
            int index = 0;
            for (int i = 0; i < parent.getChildCount(); i++) {
                if (parent.getChildAt(i) == cameraView) {
                    index = i;
                    break;
                }
            }
            parent.removeView(cameraView);
            cameraView.setPreview(newPreview);
            parent.addView(cameraView, index, params);
        }
    }

    public static class Flash extends ControlOption<com.otaliastudios.cameraview.controls.Flash> {
        Flash(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Flash.class, "Flash", hasDividerBelow);
        }
    }

    public static class WhiteBalance extends ControlOption<com.otaliastudios.cameraview.controls.WhiteBalance> {
        WhiteBalance(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.WhiteBalance.class, "White Balance", hasDividerBelow);
        }
    }

    public static class Hdr extends ControlOption<com.otaliastudios.cameraview.controls.Hdr> {
        Hdr(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Hdr.class, "HDR", hasDividerBelow);
        }
    }

    public static class VideoCodec extends ControlOption<com.otaliastudios.cameraview.controls.VideoCodec> {
        VideoCodec(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.VideoCodec.class, "Video Codec", hasDividerBelow);
        }
    }

    public static class Audio extends ControlOption<com.otaliastudios.cameraview.controls.Audio> {
        Audio(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Audio.class, "Audio", hasDividerBelow);
        }
    }

    private static abstract class GestureOption extends Option<GestureAction> {
        private final Gesture gesture;
        private final GestureAction[] allActions = GestureAction.values();

        GestureOption(@NonNull Gesture gesture, String name, boolean hasDividerBelow) {
            super(name, hasDividerBelow);
            this.gesture = gesture;
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull GestureAction value) {
            view.mapGesture(gesture, value);
        }

        @NonNull
        @Override
        public GestureAction get(@NonNull CameraView view) {
            return view.getGestureAction(gesture);
        }

        @NonNull
        @Override
        public Collection<GestureAction> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            List<GestureAction> list = new ArrayList<>();
            for (GestureAction action : allActions) {
                if (gesture.isAssignableTo(action) && options.supports(action)) {
                    list.add(action);
                }
            }
            return list;
        }
    }

    public static class Pinch extends GestureOption {
        Pinch(boolean hasDividerBelow) {
            super(Gesture.PINCH, "Pinch", hasDividerBelow);
        }
    }

    public static class HorizontalScroll extends GestureOption {
        HorizontalScroll(boolean hasDividerBelow) {
            super(Gesture.SCROLL_HORIZONTAL, "Horizontal Scroll", hasDividerBelow);
        }
    }

    public static class VerticalScroll extends GestureOption {
        VerticalScroll(boolean hasDividerBelow) {
            super(Gesture.SCROLL_VERTICAL, "Vertical Scroll", hasDividerBelow);
        }
    }

    public static class Tap extends GestureOption {
        Tap(boolean hasDividerBelow) {
            super(Gesture.TAP, "Tap", hasDividerBelow);
        }
    }

    public static class LongTap extends GestureOption {
        LongTap(boolean hasDividerBelow) {
            super(Gesture.LONG_TAP, "Long Tap", hasDividerBelow);
        }
    }

    public static class Grid extends ControlOption<com.otaliastudios.cameraview.controls.Grid> {
        Grid(boolean hasDividerBelow) {
            super(com.otaliastudios.cameraview.controls.Grid.class, "Grid Lines", hasDividerBelow);
        }
    }

    public static class GridColor extends Option<Pair<Integer, String>> {

        GridColor(boolean hasDividerBelow) {
            super("Grid Color", hasDividerBelow);
        }

        private static final List<Pair<Integer, String>> ALL = Arrays.asList(
                new Pair<>(Color.argb(160, 255, 255, 255), "default"),
                new Pair<>(Color.WHITE, "white"),
                new Pair<>(Color.BLACK, "black"),
                new Pair<>(Color.YELLOW, "yellow")
        );

        @NonNull
        @Override
        public Collection<Pair<Integer, String>> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            return ALL;
        }

        @NonNull
        @Override
        public Pair<Integer, String> get(@NonNull CameraView view) {
            for (Pair<Integer, String> pair : ALL) {
                //noinspection ConstantConditions
                if (pair.first == view.getGridColor()) {
                    return pair;
                }
            }
            throw new RuntimeException("Could not find grid color");
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull Pair<Integer, String> value) {
            //noinspection ConstantConditions
            view.setGridColor(value.first);
        }

        @NonNull
        @Override
        public String toString(@NonNull Pair<Integer, String> value) {
            //noinspection ConstantConditions
            return value.second;
        }
    }

    public static class UseDeviceOrientation extends Option<Boolean> {
        UseDeviceOrientation(boolean hasDividerBelow) {
            super("Use Device Orientation", hasDividerBelow);
        }

        @NonNull
        @Override
        public Collection<Boolean> getAll(@NonNull CameraView view, @NonNull CameraOptions options) {
            return Arrays.asList(true, false);
        }

        @NonNull
        @Override
        public Boolean get(@NonNull CameraView view) {
            return view.getUseDeviceOrientation();
        }

        @Override
        public void set(@NonNull CameraView view, @NonNull Boolean value) {
            view.setUseDeviceOrientation(value);
        }
    }
}
