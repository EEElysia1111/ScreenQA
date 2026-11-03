package com.dsh.screenqa;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayManager {

    private static final String TAG = "ScreenQA";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WindowManager windowManager;
    private static TextView pill;
    private static WindowManager.LayoutParams pillParams;
    private static View card;
    private static TextView cardBody;
    private static WindowManager.LayoutParams cardParams;
    private static Context appContext;
    private static String fullText = "";
    private static String compactText = "";

    private OverlayManager() {
    }

    public static void showLoading(Context context) {
        showPill(context, "识别中…");
    }

    public static void showThinking(Context context, boolean reasoning) {
        showPill(context, reasoning ? "深度思考中…" : "DeepSeek 解题中…");
    }

    public static void showAnswer(Context context, String answer) {
        fullText = answer == null ? "" : answer.trim();
        compactText = compact(fullText);
        showPill(context, compactText);
    }

    public static void showError(Context context, String message) {
        fullText = "⚠ " + message;
        compactText = "识别失败 · 点开";
        showPill(context, compactText);
    }

    private static void showPill(Context context, String text) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                appContext = app;
                if (windowManager == null) {
                    windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
                }
                if (card != null && card.getParent() != null) {
                    windowManager.removeView(card);
                }
                if (pill == null) {
                    buildPill(app);
                }
                pill.setText(text);
                if (pill.getParent() == null) {
                    windowManager.addView(pill, pillParams);
                } else {
                    windowManager.updateViewLayout(pill, pillParams);
                }
            } catch (Exception e) {
                Log.e(TAG, "pill show failed", e);
            }
        });
    }

    public static void expand(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        MAIN.post(() -> {
            try {
                if (appContext == null || !Settings.canDrawOverlays(appContext)) {
                    return;
                }
                if (windowManager == null) {
                    windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
                }
                if (pill != null && pill.getParent() != null) {
                    windowManager.removeView(pill);
                }
                if (card == null) {
                    buildCard(appContext);
                }
                cardBody.setText(TextUtils.isEmpty(fullText) ? "暂无内容" : fullText);
                if (card.getParent() == null) {
                    windowManager.addView(card, cardParams);
                } else {
                    windowManager.updateViewLayout(card, cardParams);
                }
            } catch (Exception e) {
                Log.e(TAG, "card show failed", e);
            }
        });
    }

    public static void collapse() {
        MAIN.post(() -> {
            try {
                if (card != null && card.getParent() != null && windowManager != null) {
                    windowManager.removeView(card);
                }
            } catch (Exception ignored) {
            }
            if (appContext != null) {
                showPill(appContext, compactText);
            }
        });
    }

    public static void removeAll() {
        MAIN.post(() -> {
            try {
                if (windowManager != null) {
                    if (pill != null && pill.getParent() != null) {
                        windowManager.removeView(pill);
                    }
                    if (card != null && card.getParent() != null) {
                        windowManager.removeView(card);
                    }
                }
            } catch (Exception ignored) {
            }
            pill = null;
            card = null;
            fullText = "";
            compactText = "";
        });
    }

    private static void buildPill(Context app) {
        int screenWidth = app.getResources().getDisplayMetrics().widthPixels;

        pill = new TextView(app);
        pill.setTextColor(Color.WHITE);
        pill.setTextSize(14);
        pill.setSingleLine(true);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setGravity(Gravity.CENTER);
        pill.setMaxWidth((int) (screenWidth * 0.82f));
        pill.setPadding(dp(app, 18), dp(app, 8), dp(app, 18), dp(app, 8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF0141418);
        background.setCornerRadius(dp(app, 30));
        background.setStroke(dp(app, 1), 0x33FFFFFF);
        pill.setBackground(background);
        pill.setElevation(dp(app, 10));
        pill.setOnClickListener(v -> expand(appContext));

        pillParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        pillParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        pillParams.y = statusBarHeight(app) + dp(app, 6);
    }

    private static void buildCard(Context app) {
        int screenWidth = app.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = app.getResources().getDisplayMetrics().heightPixels;

        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(app, 14), dp(app, 14), dp(app, 14), dp(app, 14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF0141418);
        background.setCornerRadius(dp(app, 16));
        background.setStroke(dp(app, 1), 0x33FFFFFF);
        root.setBackground(background);
        root.setElevation(dp(app, 12));

        LinearLayout header = new LinearLayout(app);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(app);
        title.setText("DeepSeek 解析");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setTypeface(null, Typeface.BOLD);
        title.setOnClickListener(v -> collapse());
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = actionButton(app, "复制");
        copy.setOnClickListener(v -> {
            ClipboardManager manager = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("ScreenQA", cardBody.getText()));
            Toast.makeText(app, "已复制", Toast.LENGTH_SHORT).show();
        });
        header.addView(copy);

        TextView settings = actionButton(app, "设置");
        settings.setOnClickListener(v -> openSettings(app));
        header.addView(settings);

        TextView collapse = actionButton(app, "收起");
        collapse.setOnClickListener(v -> collapse());
        header.addView(collapse);

        TextView close = actionButton(app, "×");
        close.setTextSize(20);
        close.setOnClickListener(v -> removeAll());
        header.addView(close);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(app);
        scroll.setFillViewport(true);
        cardBody = new TextView(app);
        cardBody.setTextColor(0xFFEDEDED);
        cardBody.setTextSize(15);
        cardBody.setLineSpacing(dp(app, 3), 1f);
        cardBody.setTextIsSelectable(true);
        cardBody.setPadding(0, dp(app, 10), 0, dp(app, 6));
        scroll.addView(cardBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(app);
        hint.setText("点“收起”回到小胶囊 · 结果仅供学习参考");
        hint.setTextColor(0xFF9A9AA0);
        hint.setTextSize(11);
        root.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cardParams = new WindowManager.LayoutParams(
                screenWidth - dp(app, 24),
                (int) (screenHeight * 0.55f),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        cardParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cardParams.y = statusBarHeight(app) + dp(app, 8);

        final int[] dragStart = new int[2];
        final float[] touchStart = new float[2];
        header.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStart[0] = event.getRawX();
                    touchStart[1] = event.getRawY();
                    dragStart[0] = cardParams.x;
                    dragStart[1] = cardParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    cardParams.x = dragStart[0] + (int) (event.getRawX() - touchStart[0]);
                    cardParams.y = dragStart[1] + (int) (event.getRawY() - touchStart[1]);
                    try {
                        windowManager.updateViewLayout(card, cardParams);
                    } catch (Exception ignored) {
                    }
                    return true;
                default:
                    return false;
            }
        });

        card = root;
    }

    private static TextView actionButton(Context app, String text) {
        TextView view = new TextView(app);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(app, 10), dp(app, 6), dp(app, 10), dp(app, 6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x33FFFFFF);
        background.setCornerRadius(dp(app, 10));
        view.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(app, 6), 0, 0, 0);
        view.setLayoutParams(lp);
        return view;
    }

    private static void openSettings(Context app) {
        Intent intent = new Intent(app, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(intent);
    }

    private static String compact(String answer) {
        String text = answer == null ? "" : answer.trim();
        String answerLine = null;
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (value.startsWith("答案")) {
                answerLine = value;
                break;
            }
        }
        if (answerLine != null) {
            text = answerLine;
        } else {
            int newline = text.indexOf('\n');
            if (newline > 0) {
                text = text.substring(0, newline);
            }
        }
        text = text.replaceAll("[*_#`>]", "").trim();
        if (text.isEmpty()) {
            text = "已出结果 · 点开";
        }
        if (text.length() > 40) {
            text = text.substring(0, 40) + "…";
        }
        return text;
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : dp(context, 30);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
