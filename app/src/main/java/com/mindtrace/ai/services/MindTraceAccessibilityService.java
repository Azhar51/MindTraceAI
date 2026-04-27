package com.mindtrace.ai.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.mindtrace.ai.service.ScrollEventTracker;
import com.mindtrace.ai.ui.OverUsageAlertActivity;

public class MindTraceAccessibilityService extends AccessibilityService {

    private static final String TAG = "MindTraceA11y";
    private String currentPackage = "";

    // ── Scroll tracking state ──
    /** Last scroll event timestamp (for per-package throttling). */
    private long lastScrollEventTime = 0;
    /** Last package that generated a scroll event. */
    private String lastScrollPackage = "";

    // Target packages to monitor
    private static final String[] TARGET_PACKAGES = {
            "com.whatsapp",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.snapchat.android",
            "com.facebook.katana"
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        
        String packageName = event.getPackageName().toString();

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                if (!packageName.equals(currentPackage)) {
                    currentPackage = packageName;
                    handleAppChange(packageName);
                }
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                // Reels/Shorts Blocker Logic
                detectShortFormContent(packageName, getRootInActiveWindow());
                break;

            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                handleScrollEvent(event, packageName);
                break;
        }
    }

    private void handleAppChange(String packageName) {
        boolean isTarget = false;
        for (String target : TARGET_PACKAGES) {
            if (target.equals(packageName)) {
                isTarget = true;
                break;
            }
        }

        if (isTarget) {
            Log.d(TAG, "Target app opened: " + packageName);
            // Launch the floating timer
            Intent timerIntent = new Intent(this, FloatingTimerService.class);
            startService(timerIntent);
            
            // Note: Fast Challenge or Over Usage Alert logic would go here
            // e.g., if (usage > limit) launch OverUsageAlertActivity
        } else {
            // App closed or switched to non-target
            Log.d(TAG, "Switched to: " + packageName);
            Intent timerIntent = new Intent(this, FloatingTimerService.class);
            stopService(timerIntent);
        }
    }

    private void detectShortFormContent(String packageName, AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return;

        boolean foundShorts = false;
        
        // Very basic detection based on view IDs or text commonly used in short-form video sections
        // Note: Real implementations require reverse-engineering view IDs which change frequently.
        if (packageName.equals("com.google.android.youtube")) {
            foundShorts = findNodeByContentDescription(rootNode, "Shorts") || findNodeByText(rootNode, "Shorts");
        } else if (packageName.equals("com.instagram.android")) {
            foundShorts = findNodeByContentDescription(rootNode, "Reels") || findNodeByText(rootNode, "Reels");
        }
        
        if (foundShorts) {
            Log.d(TAG, "DETECTED DOOMSCROLLING REELS/SHORTS in " + packageName);
            // We would block it here by launching an overlay activity
            // blockReels(packageName);
        }
    }

    private boolean findNodeByContentDescription(AccessibilityNodeInfo node, String keyword) {
        if (node == null) return false;
        if (node.getContentDescription() != null && node.getContentDescription().toString().toLowerCase().contains(keyword.toLowerCase())) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findNodeByContentDescription(node.getChild(i), keyword)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean findNodeByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().equalsIgnoreCase(text)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findNodeByText(node.getChild(i), text)) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SCROLL TRACKING (Phase 4)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Handle TYPE_VIEW_SCROLLED events with per-package throttling.
     *
     * <p>Scroll events fire at very high frequency (10-50x/sec during a fling).
     * We throttle to one event per 500ms per package and delegate to
     * {@link ScrollEventTracker} for SharedPreferences persistence.</p>
     *
     * <p>We extract the scroll delta from the event record when available.
     * On API 28+ the fromIndex/toIndex contain scroll offsets; before that
     * we use itemCount as a rough proxy for distance.</p>
     */
    private void handleScrollEvent(AccessibilityEvent event, String packageName) {
        long now = System.currentTimeMillis();

        // Per-package throttle: skip if same package scrolled <500ms ago
        if (packageName.equals(lastScrollPackage)
                && (now - lastScrollEventTime) < 500) {
            return;
        }

        lastScrollEventTime = now;
        lastScrollPackage = packageName;

        // Extract scroll delta (best effort)
        int scrollDelta = 0;
        if (event.getScrollDeltaY() != -1) {
            // API 28+ provides direct delta
            scrollDelta = Math.abs(event.getScrollDeltaY());
        } else if (event.getItemCount() > 0) {
            // Fallback: use itemCount as rough proxy (number of items scrolled through)
            scrollDelta = event.getItemCount() * 50; // ~50px per item estimate
        }

        // Delegate to the tracker
        ScrollEventTracker.recordScrollEvent(
                getApplicationContext(), packageName, scrollDelta);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service Interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
        Log.d(TAG, "MindTrace Accessibility Service Connected (scroll tracking enabled)");
    }
}
