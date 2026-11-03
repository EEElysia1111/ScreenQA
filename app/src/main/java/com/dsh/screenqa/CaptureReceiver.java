package com.dsh.screenqa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class CaptureReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.dsh.screenqa.CAPTURE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
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
}
