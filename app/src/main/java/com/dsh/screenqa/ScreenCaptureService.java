package com.dsh.screenqa;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class ScreenCaptureService extends AccessibilityService {

    public interface Callback {
        void onBitmap(Bitmap bitmap);

        void onError(String message);
    }

    private static volatile ScreenCaptureService instance;

    public static boolean isConnected() {
        return instance != null;
    }

    /**
     * Reads the text of the current window from the accessibility tree. This is
     * much more accurate than OCR for WebView based question pages.
     */
    public static String extractText() {
        ScreenCaptureService service = instance;
        if (service == null) {
            return "";
        }
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            appendNodeText(root, builder, 0);
            return builder.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void appendNodeText(AccessibilityNodeInfo node, StringBuilder builder, int depth) {
        if (node == null || depth > 40 || builder.length() > 12000) {
            return;
        }
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String value = text.toString().trim();
            if (!value.isEmpty() && builder.indexOf(value) < 0) {
                builder.append(value).append('\n');
            }
        }
        CharSequence description = node.getContentDescription();
        if (description != null && description.length() > 0) {
            String value = description.toString().trim();
            if (!value.isEmpty() && builder.indexOf(value) < 0) {
                builder.append(value).append('\n');
            }
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            try {
                appendNodeText(node.getChild(i), builder, depth + 1);
            } catch (Exception ignored) {
            }
        }
    }

    public static void capture(Callback callback) {
        ScreenCaptureService service = instance;
        if (service == null) {
            callback.onError("无障碍截屏服务未启用");
            return;
        }
        if (Build.VERSION.SDK_INT < 30) {
            callback.onError("系统版本低于 Android 11，无法静默截屏");
            return;
        }
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult result) {
                        HardwareBuffer buffer = null;
                        try {
                            buffer = result.getHardwareBuffer();
                            Bitmap wrapped = Bitmap.wrapHardwareBuffer(
                                    buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                            if (wrapped == null) {
                                callback.onError("截图解码失败");
                                return;
                            }
                            Bitmap copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                            if (copy == null) {
                                callback.onError("截图转换失败");
                            } else {
                                callback.onBitmap(copy);
                            }
                        } catch (Exception e) {
                            callback.onError("截图失败：" + e.getMessage());
                        } finally {
                            if (buffer != null) {
                                buffer.close();
                            }
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        callback.onError("系统拒绝截屏，错误码 " + errorCode);
                    }
                });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
