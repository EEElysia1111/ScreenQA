package com.dsh.screenqa;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.WindowManager;

public class TriggerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        ScreenCaptureService.capture(new ScreenCaptureService.Callback() {
            @Override
            public void onBitmap(android.graphics.Bitmap bitmap) {
                QuestionProcessor.process(getApplicationContext(), bitmap);
                finish();
            }

            @Override
            public void onError(String message) {
                OverlayManager.showError(getApplicationContext(), message);
                finish();
            }
        });
    }
}
