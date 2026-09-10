package com.yukino.counter;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.POINT;

import java.awt.*;
import java.util.concurrent.*;
import java.util.function.DoubleConsumer;

/** Reads cursor coordinates only; it never installs a mouse hook. */
public final class MouseDistanceTracker implements AutoCloseable {
    private final DoubleConsumer listener;
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mouse-distance-sampler");
        t.setDaemon(true);
        return t;
    });
    private final POINT point = new POINT();
    private final double metersPerPixel;
    private int lastX, lastY;
    private boolean initialized;

    public MouseDistanceTracker(DoubleConsumer listener) {
        this.listener = listener;
        int dpi = Math.max(72, Toolkit.getDefaultToolkit().getScreenResolution());
        metersPerPixel = 0.0254d / dpi;
    }

    public void start() { sampler.scheduleAtFixedRate(this::sample, 0, 20, TimeUnit.MILLISECONDS); }

    private void sample() {
        if (!User32.INSTANCE.GetCursorPos(point)) return;
        int x = point.x, y = point.y;
        if (initialized) {
            double pixels = Math.hypot(x - lastX, y - lastY);
            if (pixels > 0 && pixels < 5000) listener.accept(pixels * metersPerPixel);
        }
        lastX = x; lastY = y; initialized = true;
    }

    @Override public void close() {
        sampler.shutdownNow();
        try { sampler.awaitTermination(1, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
