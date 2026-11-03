package com.dsh.screenqa;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final int REQ_KB = 200;

    private EditText keyInput;
    private EditText modelInput;
    private EditText reasonerInput;
    private EditText baseInput;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF101014);
        getWindow().setNavigationBarColor(0xFF101014);
        buildUi();
        load();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101014);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("ScreenQA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("双击背面静默截屏，识别题目后调用 DeepSeek。顶部小胶囊只显示答案，点一下展开解析。");
        subtitle.setTextColor(0xFFB8B8C0);
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(dp(3), 1f);
        root.addView(subtitle, topMargin(dp(8)));

        addLabel(root, "DeepSeek API Key");
        keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setSingleLine(true);
        keyInput.setTextSize(14);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(keyInput);
        root.addView(keyInput, topMargin(dp(6)));

        addLabel(root, "选择题模型");
        modelInput = new EditText(this);
        modelInput.setHint("deepseek-chat");
        modelInput.setSingleLine(true);
        modelInput.setTextSize(14);
        styleInput(modelInput);
        root.addView(modelInput, topMargin(dp(6)));

        addLabel(root, "填空/简答模型（深度思考）");
        reasonerInput = new EditText(this);
        reasonerInput.setHint("deepseek-reasoner");
        reasonerInput.setSingleLine(true);
        reasonerInput.setTextSize(14);
        styleInput(reasonerInput);
        root.addView(reasonerInput, topMargin(dp(6)));

        addLabel(root, "API 地址");
        baseInput = new EditText(this);
        baseInput.setHint("https://api.deepseek.com");
        baseInput.setSingleLine(true);
        baseInput.setTextSize(14);
        styleInput(baseInput);
        root.addView(baseInput, topMargin(dp(6)));

        Button save = button("保存");
        save.setOnClickListener(v -> save());
        root.addView(save, topMargin(dp(18)));

        Button test = button("测试 DeepSeek 连接");
        test.setOnClickListener(v -> testConnection());
        root.addView(test, topMargin(dp(8)));

        Button knowledge = button("导入知识库 .txt");
        knowledge.setOnClickListener(v -> pickKnowledgeFiles());
        root.addView(knowledge, topMargin(dp(8)));

        statusView = new TextView(this);
        statusView.setTextColor(0xFFB8B8C0);
        statusView.setTextSize(13);
        statusView.setLineSpacing(dp(3), 1f);
        root.addView(statusView, topMargin(dp(18)));

        addDivider(root);

        Button overlay = button("授予悬浮窗权限");
        overlay.setOnClickListener(v -> openOverlaySettings());
        root.addView(overlay, topMargin(dp(8)));

        Button accessibility = button("打开无障碍设置（启用静默截屏）");
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(accessibility, topMargin(dp(8)));

        Button taptap = button("打开 TapTap 设置");
        taptap.setOnClickListener(v -> openTapTap());
        root.addView(taptap, topMargin(dp(8)));

        Button closeOverlay = button("关闭当前悬浮窗");
        closeOverlay.setOnClickListener(v -> {
            OverlayManager.removeAll();
            Toast.makeText(this, "已关闭悬浮窗", Toast.LENGTH_SHORT).show();
        });
        root.addView(closeOverlay, topMargin(dp(8)));

        TextView tip = new TextView(this);
        tip.setText("使用步骤：\n"
                + "1. 填入 DeepSeek API Key 并保存\n"
                + "2. 授予悬浮窗权限\n"
                + "3. 打开无障碍设置，启用 ScreenQA\n"
                + "4. TapTap → 设置 → 双击动作 → 启动应用 → 选择 ScreenQA\n"
                + "5. 题目页双击背面，顶部小胶囊显示答案，点一下看解析\n\n"
                + "静默截屏不会保存到相册，也不会弹出截图预览；图片只在本地做 OCR，"
                + "发给 DeepSeek 的是识别后的文字。");
        tip.setTextColor(0xFF8F8F98);
        tip.setTextSize(12);
        tip.setLineSpacing(dp(3), 1f);
        root.addView(tip, topMargin(dp(20)));
    }

    private void load() {
        keyInput.setText(Prefs.apiKey(this));
        modelInput.setText(Prefs.model(this));
        reasonerInput.setText(Prefs.reasonerModel(this));
        baseInput.setText(Prefs.baseUrl(this));
    }

    private void save() {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "请先填入 DeepSeek API Key", Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.save(this, key, modelInput.getText().toString(), baseInput.getText().toString(),
                reasonerInput.getText().toString());
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void testConnection() {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "请先填入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        statusView.setText("正在测试 DeepSeek 连接…");
        DeepSeekClient.ask(key,
                baseInput.getText().toString(),
                modelInput.getText().toString(),
                "请回答：1+1 等于几？只回答数字。",
                new DeepSeekClient.Callback() {
                    @Override
                    public void onSuccess(String answer) {
                        runOnUiThread(() -> statusView.setText("连接成功，模型回复：\n" + answer));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> statusView.setText("连接失败：\n" + message));
                    }
                });
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开悬浮窗设置：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开无障碍设置：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openTapTap() {
        Intent intent = new Intent();
        intent.setClassName("com.kieronquinn.app.taptap",
                "com.kieronquinn.app.taptap.ui.activities.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "没有找到 TapTap", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        boolean key = Prefs.hasKey(this);
        boolean overlay = Settings.canDrawOverlays(this);
        boolean accessibility = ScreenCaptureService.isConnected();
        statusView.setText("当前状态\n"
                + (key ? "✅" : "❌") + " API Key\n"
                + (overlay ? "✅" : "❌") + " 悬浮窗权限\n"
                + (accessibility ? "✅" : "❌") + " 无障碍截屏服务\n"
                + "📚 本地知识库：" + LocalKnowledge.status(this));
    }

    private void pickKnowledgeFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_KB);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_KB || resultCode != RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        File dir = new File(getExternalFilesDir(null), "kb");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        int copied = 0;
        for (Uri uri : uris) {
            try {
                String name = queryDisplayName(uri);
                if (name == null || !name.toLowerCase().endsWith(".txt")) {
                    name = "kb_" + System.currentTimeMillis() + ".txt";
                }
                File target = new File(dir, name);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(target)) {
                    if (in == null) {
                        continue;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                copied++;
            } catch (Exception e) {
                Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        LocalKnowledge.refresh(this);
        Toast.makeText(this, "已导入 " + copied + " 个文件", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("ScreenQA", "settings hasKey=" + Prefs.hasKey(this)
                + " len=" + Prefs.apiKey(this).length());
        refreshStatus();
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFFE6E6EA);
        label.setTextSize(13);
        label.setTypeface(null, Typeface.BOLD);
        root.addView(label, topMargin(dp(16)));
    }

    private void addDivider(LinearLayout root) {
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFF2A2A33);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(20);
        root.addView(divider, lp);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF2A2A33);
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        return button;
    }

    private void styleInput(EditText input) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1C1C22);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), 0xFF33333A);
        input.setBackground(background);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xFF777780);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
    }

    private LinearLayout.LayoutParams topMargin(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
