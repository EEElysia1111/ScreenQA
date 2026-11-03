package com.dsh.screenqa;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.concurrent.atomic.AtomicInteger;

public final class QuestionProcessor {

    private static final AtomicInteger LATEST_REQUEST = new AtomicInteger(0);
    private static TextRecognizer recognizer;

    private QuestionProcessor() {
    }

    public static void process(Context context, Bitmap bitmap) {
        final int requestId = LATEST_REQUEST.incrementAndGet();
        if (bitmap == null) {
            if (isLatest(requestId)) {
                OverlayManager.showError(context, "没有拿到截图");
            }
            return;
        }
        Context app = context.getApplicationContext();
        String nodeText = ScreenCaptureService.extractText();
        if (isUsable(nodeText)) {
            if (!isLatest(requestId)) {
                return;
            }
            OverlayManager.showLoading(app);
            handle(app, nodeText, requestId);
            return;
        }
        if (!isLatest(requestId)) {
            return;
        }
        OverlayManager.showLoading(app);
        Bitmap cropped = crop(bitmap);
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        }
        recognizer.process(InputImage.fromBitmap(cropped, 0))
                .addOnSuccessListener(result -> {
                    if (isLatest(requestId)) {
                        handle(app, result.getText(), requestId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isLatest(requestId)) {
                        OverlayManager.showError(app, "OCR 失败：" + e.getMessage());
                    }
                });
    }

    private static boolean isLatest(int requestId) {
        return requestId == LATEST_REQUEST.get();
    }

    private static boolean isUsable(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            }
        }
        return cjk >= 25
                || text.contains("____")
                || text.contains("___")
                || (cjk >= 12 && (text.contains("？") || text.contains("?")));
    }

    private static Bitmap crop(Bitmap bitmap) {
        try {
            int top = (int) (bitmap.getHeight() * 0.035f);
            int bottom = (int) (bitmap.getHeight() * 0.965f);
            if (bottom > top) {
                return Bitmap.createBitmap(bitmap, 0, top, bitmap.getWidth(), bottom - top);
            }
        } catch (Exception ignored) {
        }
        return bitmap;
    }

    private static void handle(Context context, String text, int requestId) {
        if (!isLatest(requestId)) {
            return;
        }
        String cleaned = text == null ? "" : text
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (cleaned.length() < 5) {
            OverlayManager.showError(context, "没有识别到足够的文字，请确认题目在屏幕内。");
            return;
        }
        if (!Prefs.hasKey(context)) {
            OverlayManager.showError(context,
                    "还没有填 DeepSeek API Key。\n打开 ScreenQA 设置填入后重试。\n\n识别到的文字：\n"
                            + truncate(cleaned, 300));
            return;
        }
        String qa = LocalKnowledge.match(context, cleaned);
        if (qa != null) {
            String answer = qa.startsWith("答案") ? qa : "答案：" + qa;
            OverlayManager.showAnswer(context, answer + "\n\n（来自本地题库）");
            return;
        }
        String reference = LocalKnowledge.retrieve(context, cleaned, 4, 2500);
        final String chatModel = Prefs.model(context);
        final String reasonerModel = Prefs.reasonerModel(context);
        final boolean useReasoner = shouldUseReasoner(cleaned);
        final String model = useReasoner ? reasonerModel : chatModel;
        OverlayManager.showThinking(context, useReasoner);
        ask(context, cleaned, reference, model, useReasoner, chatModel, requestId);
    }

    private static void ask(Context context, String text, String reference, String model,
                            boolean useReasoner, String chatModel, int requestId) {
        if (!isLatest(requestId)) {
            return;
        }
        String userText = "题目文本：\n" + text;
        if (reference != null && !reference.isEmpty()) {
            userText += "\n\n【本地资料摘录（可能相关，若无关请忽略）】\n" + reference;
        }
        DeepSeekClient.ask(Prefs.apiKey(context), Prefs.baseUrl(context), model, userText,
                new DeepSeekClient.Callback() {
                    @Override
                    public void onSuccess(String answer) {
                        if (isLatest(requestId)) {
                            OverlayManager.showAnswer(context, answer);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isLatest(requestId)) {
                            return;
                        }
                        if (useReasoner && !model.equals(chatModel)) {
                            OverlayManager.showThinking(context, false);
                            ask(context, text, reference, chatModel, false, chatModel, requestId);
                        } else {
                            OverlayManager.showError(context, message);
                        }
                    }
                });
    }

    private static boolean shouldUseReasoner(String text) {
        if (text == null) {
            return false;
        }
        boolean choice = text.contains("选择题")
                || text.contains("单选题")
                || text.contains("多选题")
                || text.contains("判断题");
        int options = 0;
        for (String letter : new String[]{"A", "B", "C", "D"}) {
            if (text.matches("(?s).*(?:^|\\n)\\s*" + letter + "\\s*[\\.、．:：].*")) {
                options++;
            }
        }
        if (choice || options >= 2) {
            return false;
        }
        return text.contains("填空")
                || text.contains("简答")
                || text.contains("论述")
                || text.contains("名词解释")
                || text.contains("____")
                || text.contains("___");
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
