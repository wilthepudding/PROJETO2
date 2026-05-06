package com.projeto2.pianoplayer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class PianoAccessibilityService extends AccessibilityService {
    public static PianoAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public boolean tap(float x, float y) {
        return multiTap(new float[]{x}, new float[]{y}, 38);
    }

    public boolean multiTap(float[] xs, float[] ys, long durationMs) {
        if (Build.VERSION.SDK_INT < 24 || xs == null || ys == null) return false;
        int count = Math.min(xs.length, ys.length);
        if (count <= 0) return false;
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int i = 0; i < count; i++) {
            Path p = new Path();
            p.moveTo(xs[i], ys[i]);
            builder.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(20, durationMs)));
        }
        return dispatchGesture(builder.build(), null, null);
    }
}
