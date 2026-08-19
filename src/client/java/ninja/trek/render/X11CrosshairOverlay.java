package ninja.trek.render;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.ptr.IntByReference;
import net.minecraft.client.Minecraft;
import ninja.trek.Craneshot;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

/**
 * A tiny X11 top-level window used as an OBS-safe camera crosshair.
 *
 * <p>The class is safe to load on other platforms. Xlib and Xext are only
 * resolved lazily after {@link #isSupported()} confirms that GLFW itself is
 * using its X11 backend.</p>
 */
public final class X11CrosshairOverlay implements AutoCloseable {
    private static final int SHAPE_SET = 0;
    private static final int SHAPE_INPUT = 2;
    private static final int UNSORTED = 0;
    private static final int WHITE_PIXEL = 0x00FFFFFF;

    private X11 x11;
    private XlibOverrides xlibOverrides;
    private XShape xShape;
    private X11.Display display;
    private X11.Window rootWindow;
    private X11.Window overlayWindow;
    private boolean mapped;
    private boolean failed;
    private boolean failureLogged;
    private int currentSize;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    public enum ShowResult {
        SHOWN,
        HIDDEN,
        FAILED
    }

    public static boolean isSupported() {
        if (Platform.get() != Platform.LINUX) {
            return false;
        }

        try {
            return GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_X11;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean hasFailed() {
        return failed;
    }

    public boolean isMapped() {
        return mapped;
    }

    public ShowResult show(Minecraft client, double normalizedX, double normalizedY, int requestedSize) {
        if (!Double.isFinite(normalizedX)
                || !Double.isFinite(normalizedY)
                || normalizedX < -1.0
                || normalizedX > 1.0
                || normalizedY < -1.0
                || normalizedY > 1.0) {
            hide();
            return ShowResult.HIDDEN;
        }

        int size = Math.max(1, requestedSize);
        if (failed || !ensureInitialized(size)) {
            return ShowResult.FAILED;
        }

        try {
            int width = client.getWindow().getWidth();
            int height = client.getWindow().getHeight();
            if (width <= 0 || height <= 0) {
                hide();
                return ShowResult.HIDDEN;
            }

            int pixelX = clampToWindow((normalizedX + 1.0) * 0.5 * width, width);
            int pixelY = clampToWindow((1.0 - normalizedY) * 0.5 * height, height);
            int x = client.getWindow().getX() + pixelX - size / 2;
            int y = client.getWindow().getY() + pixelY - size / 2;

            if (!mapped) {
                if (!performChecked("Could not map the X11 camera-dot window", () -> {
                    if (size != currentSize) {
                        x11.XResizeWindow(display, overlayWindow, size, size);
                    }
                    x11.XMoveWindow(display, overlayWindow, x, y);
                    x11.XMapRaised(display, overlayWindow);
                })) {
                    return ShowResult.FAILED;
                }
                mapped = true;
                currentSize = size;
                lastX = x;
                lastY = y;
            } else {
                if (size != currentSize) {
                    x11.XResizeWindow(display, overlayWindow, size, size);
                    currentSize = size;
                }
                if (x != lastX || y != lastY) {
                    x11.XMoveWindow(display, overlayWindow, x, y);
                    lastX = x;
                    lastY = y;
                }
                x11.XRaiseWindow(display, overlayWindow);
                x11.XFlush(display);
            }
            return ShowResult.SHOWN;
        } catch (Throwable throwable) {
            return fail("Failed to update the X11 camera dot", throwable);
        }
    }

    public void hide() {
        if (!mapped || display == null || overlayWindow == null) {
            return;
        }

        try {
            performChecked(
                    "Could not unmap the X11 camera-dot window",
                    () -> x11.XUnmapWindow(display, overlayWindow));
        } catch (Throwable throwable) {
            fail("Failed to hide the X11 camera dot", throwable);
        } finally {
            mapped = false;
        }
    }

    @Override
    public void close() {
        destroyNativeResources();
    }

    private boolean ensureInitialized(int initialSize) {
        if (overlayWindow != null) {
            return true;
        }
        if (failed || !isSupported()) {
            return false;
        }

        try {
            x11 = X11.INSTANCE;
            xlibOverrides = Native.load("X11", XlibOverrides.class);
            xShape = Native.load("Xext", XShape.class);
            display = x11.XOpenDisplay(null);
            if (display == null || display.getPointer() == null) {
                fail("Could not open the X11 display", null);
                return false;
            }

            IntByReference shapeEventBase = new IntByReference();
            IntByReference shapeErrorBase = new IntByReference();
            if (xShape.XShapeQueryExtension(display, shapeEventBase, shapeErrorBase) == 0) {
                fail("The X Shape extension is unavailable", null);
                return false;
            }

            rootWindow = x11.XDefaultRootWindow(display);
            boolean initialized = performChecked("Could not configure the X11 camera-dot window", () -> {
                overlayWindow = x11.XCreateSimpleWindow(
                        display,
                        rootWindow,
                        -initialSize,
                        -initialSize,
                        initialSize,
                        initialSize,
                        0,
                        WHITE_PIXEL,
                        WHITE_PIXEL);

                if (overlayWindow == null || overlayWindow.longValue() == 0L) {
                    return;
                }

                NativeWindowAttributes attributes = new NativeWindowAttributes();
                attributes.overrideRedirect = 1;
                attributes.write();
                xlibOverrides.XChangeWindowAttributes(
                        display,
                        overlayWindow,
                        new NativeLong(X11.CWOverrideRedirect),
                        attributes);

                xShape.XShapeCombineRectangles(
                        display,
                        overlayWindow,
                        SHAPE_INPUT,
                        0,
                        0,
                        Pointer.NULL,
                        0,
                        SHAPE_SET,
                        UNSORTED);
            });
            if (overlayWindow == null || overlayWindow.longValue() == 0L) {
                fail("Could not create the X11 camera-dot window", null);
                return false;
            }
            currentSize = initialSize;
            return initialized;
        } catch (Throwable throwable) {
            fail("Could not initialize the X11 camera dot", throwable);
            return false;
        }
    }

    private ShowResult fail(String message, Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            if (throwable == null) {
                Craneshot.LOGGER.warn("{}; falling back to the in-game crosshair", message);
            } else {
                Craneshot.LOGGER.warn("{}; falling back to the in-game crosshair", message, throwable);
            }
        }
        failed = true;
        destroyNativeResources();
        return ShowResult.FAILED;
    }

    private void destroyNativeResources() {
        X11.Display displayToClose = display;
        X11 api = x11;

        display = null;
        rootWindow = null;
        overlayWindow = null;
        mapped = false;
        currentSize = 0;
        lastX = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;

        if (api == null || displayToClose == null) {
            return;
        }

        try {
            api.XCloseDisplay(displayToClose);
        } catch (Throwable ignored) {
            // Nothing else can be released after the display is closed.
        }
    }

    /**
     * Runs requests through an XSync while temporarily replacing Xlib's fatal
     * default error handler. X protocol errors are asynchronous and cannot be
     * caught as Java exceptions, so this is required around setup/state
     * transitions that may legitimately fail on an unusual X server.
     */
    private boolean performChecked(String operation, Runnable requests) {
        XErrorCapture capture = new XErrorCapture(display);
        X11.XErrorHandler previousHandler = x11.XSetErrorHandler(capture);
        capture.setPreviousHandler(previousHandler);

        try {
            requests.run();
            x11.XSync(display, false);
        } finally {
            x11.XSetErrorHandler(previousHandler);
        }

        if (!capture.hasError()) {
            return true;
        }

        fail(operation + " (X11 error " + capture.errorCode
                + ", request " + capture.requestCode + ")", null);
        return false;
    }

    private static int clampToWindow(double coordinate, int size) {
        return Math.max(0, Math.min(size - 1, (int) Math.round(coordinate)));
    }

    private static final class XErrorCapture implements X11.XErrorHandler {
        private final X11.Display targetDisplay;
        private X11.XErrorHandler previousHandler;
        private int errorCode;
        private int requestCode;

        private XErrorCapture(X11.Display targetDisplay) {
            this.targetDisplay = targetDisplay;
        }

        private void setPreviousHandler(X11.XErrorHandler previousHandler) {
            this.previousHandler = previousHandler;
        }

        private boolean hasError() {
            return errorCode != 0;
        }

        @Override
        public int apply(X11.Display errorDisplay, X11.XErrorEvent errorEvent) {
            if (sameDisplay(errorDisplay, targetDisplay)) {
                errorCode = Byte.toUnsignedInt(errorEvent.error_code);
                requestCode = Byte.toUnsignedInt(errorEvent.request_code);
                return 0;
            }

            return previousHandler == null ? 0 : previousHandler.apply(errorDisplay, errorEvent);
        }

        private static boolean sameDisplay(X11.Display first, X11.Display second) {
            return first != null
                    && second != null
                    && first.getPointer() != null
                    && first.getPointer().equals(second.getPointer());
        }
    }

    /**
     * JNA maps Java {@code boolean true} to the native value {@code -1}. Xlib's
     * protocol requires Bool window attributes to be exactly 0 or 1, so the
     * stock XSetWindowAttributes mapping cannot be used for override_redirect.
     */
    @Structure.FieldOrder({
            "backgroundPixmap",
            "backgroundPixel",
            "borderPixmap",
            "borderPixel",
            "bitGravity",
            "winGravity",
            "backingStore",
            "backingPlanes",
            "backingPixel",
            "saveUnder",
            "eventMask",
            "doNotPropagateMask",
            "overrideRedirect",
            "colormap",
            "cursor"
    })
    public static final class NativeWindowAttributes extends Structure {
        public X11.Pixmap backgroundPixmap;
        public NativeLong backgroundPixel;
        public X11.Pixmap borderPixmap;
        public NativeLong borderPixel;
        public int bitGravity;
        public int winGravity;
        public int backingStore;
        public NativeLong backingPlanes;
        public NativeLong backingPixel;
        public int saveUnder;
        public NativeLong eventMask;
        public NativeLong doNotPropagateMask;
        public int overrideRedirect;
        public X11.Colormap colormap;
        public X11.Cursor cursor;
    }

    private interface XlibOverrides extends Library {
        int XChangeWindowAttributes(
                X11.Display display,
                X11.Window window,
                NativeLong valueMask,
                NativeWindowAttributes attributes);
    }

    private interface XShape extends Library {
        int XShapeQueryExtension(
                X11.Display display,
                IntByReference eventBase,
                IntByReference errorBase);

        void XShapeCombineRectangles(
                X11.Display display,
                X11.Window window,
                int destinationKind,
                int xOffset,
                int yOffset,
                Pointer rectangles,
                int rectangleCount,
                int operation,
                int ordering);
    }
}
