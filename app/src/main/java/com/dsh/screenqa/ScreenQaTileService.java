package com.dsh.screenqa;

import android.service.quicksettings.TileService;

public class ScreenQaTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        if (!ScreenCaptureService.isConnected()) {
            return;
        }
        ScreenCaptureService.capture(new ScreenCaptureService.Callback() {
            @Override
            public void onBitmap(android.graphics.Bitmap bitmap) {
                QuestionProcessor.process(getApplicationContext(), bitmap);
            }

            @Override
            public void onError(String message) {
                OverlayManager.showError(getApplicationContext(), message);
            }
        });
    }
}
