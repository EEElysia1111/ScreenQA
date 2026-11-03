package com.dsh.screenqa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public class CaptureReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.screenqa.CAPTURE";

    /**
     * 唯一允许触发截屏的发送方：TapTap 补丁版。
     * 只在 Android 14+ 能校验（BroadcastReceiver#getSentFromPackage）。
     */
    private static final String ALLOWED_SENDER = "com.kieronquinn.app.taptap";

    /** 两次触发的最小间隔。 */
    private static final long MIN_INTERVAL_MS = 2000L;
    /** 限流窗口长度。 */
    private static final long WINDOW_MS = 60_000L;
    /** 每个窗口内最多触发次数。 */
    private static final int MAX_PER_WINDOW = 6;

    private static final String KEY_LAST = "trigger_last_ms";
    private static final String KEY_WINDOW_START = "trigger_window_start_ms";
    private static final String KEY_WINDOW_COUNT = "trigger_window_count";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            return;
        }
        if (!isAllowedSender()) {
            return;
        }
        Context app = context.getApplicationContext();
        if (!allowByRateLimit(app)) {
            return;
        }
        OverlayManager.showLoading(app);
        // Give the WebView/accessibility tree a moment to settle after switching
        // to the next question, otherwise we may read the previous page.
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                ScreenCaptureService.capture(new ScreenCaptureService.Callback() {
                    @Override
                    public void onBitmap(android.graphics.Bitmap bitmap) {
                        QuestionProcessor.process(app, bitmap);
                    }

                    @Override
                    public void onError(String message) {
                        OverlayManager.showError(app, message);
                    }
                }), 450);
    }

    /**
     * Android 14 起广播可以拿到发送方包名，只放行 TapTap 补丁。
     * 更低版本没有可靠 API，只能依赖限流，见 SECURITY.md 的已知限制。
     */
    private boolean isAllowedSender() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        return ALLOWED_SENDER.equals(getSentFromPackage());
    }

    /** 2 秒冷却 + 每分钟上限，限制被其他应用滥用时的消耗。 */
    private boolean allowByRateLimit(Context app) {
        SharedPreferences prefs = Prefs.get(app);
        long now = System.currentTimeMillis();

        long last = prefs.getLong(KEY_LAST, 0L);
        if (now - last < MIN_INTERVAL_MS) {
            return false;
        }

        long windowStart = prefs.getLong(KEY_WINDOW_START, 0L);
        int count = prefs.getInt(KEY_WINDOW_COUNT, 0);
        if (now - windowStart > WINDOW_MS) {
            windowStart = now;
            count = 0;
        }
        if (count >= MAX_PER_WINDOW) {
            return false;
        }

        prefs.edit()
                .putLong(KEY_LAST, now)
                .putLong(KEY_WINDOW_START, windowStart)
                .putInt(KEY_WINDOW_COUNT, count + 1)
                .apply();
        return true;
    }
}
