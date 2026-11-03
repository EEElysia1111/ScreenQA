package com.dsh.screenqa;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class DeepSeekClient {

    public interface Callback {
        void onSuccess(String answer);

        void onError(String message);
    }

    private DeepSeekClient() {
    }

    public static void ask(final String apiKey,
                           final String baseUrl,
                           final String model,
                           final String questionText,
                           final Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String base = baseUrl == null ? "https://api.deepseek.com" : baseUrl.trim();
                while (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                URL url = new URL(base + "/chat/completions");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(90000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());

                JSONObject body = new JSONObject();
                body.put("model", model);
                if (!model.contains("reasoner")) {
                    body.put("temperature", 0.0);
                }
                body.put("max_tokens", 1200);
                body.put("stream", false);

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "你是一个严谨的做题助手。用户会给你一段从手机屏幕上 OCR 出来的题目文本，"
                                + "其中可能混有状态栏、按钮、页码、输入框等界面文字。"
                                + "请先判断题型，再按题型作答：\n"
                                + "1. 选择题：找到题干和全部选项，逐项分析，只选一个正确答案。格式：\n"
                                + "答案：<选项字母> <该选项完整原文>\n"
                                + "解析：<2 到 4 句理由>\n"
                                + "2. 填空题：直接给出每个空应填的内容，多个空按 ①②③ 列出。格式：\n"
                                + "答案：①... ②...\n"
                                + "解析：<简要说明>\n"
                                + "3. 简答题：分点给出核心答案，2 到 5 点。格式：\n"
                                + "答案：1. ... 2. ...\n"
                                + "解析：<必要时补充>\n"
                                + "4. 判断题：答案：正确 或 错误。\n"
                                + "5. 计算题：答案：<最终结果>；解析：<关键步骤>。\n"
                                + "注意：不要因为没有选项就回答“信息不足”；只有确实看不到题干时才说“信息不足”。"
                                + "选择题的字母必须与选项原文严格对应，发现不一致要重新核对。"
                                + "如果用户消息里包含【本地资料摘录】，优先依据资料作答；资料与题目无关时忽略。"));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", questionText));
                body.put("messages", messages);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream stream = (code >= 200 && code < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String response = readAll(stream);

                if (code < 200 || code >= 300) {
                    callback.onError("HTTP " + code + "\n" + trim(response));
                    return;
                }

                JSONObject json = new JSONObject(response);
                JSONArray choices = json.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    callback.onError("API 返回中没有 choices：\n" + trim(response));
                    return;
                }
                String content = choices.getJSONObject(0)
                        .optJSONObject("message")
                        .optString("content", "");
                if (content.trim().isEmpty()) {
                    callback.onError("DeepSeek 返回了空内容");
                } else {
                    callback.onSuccess(content.trim());
                }
            } catch (Exception e) {
                callback.onError(e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "deepseek-request").start();
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    private static String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 1200 ? text.substring(0, 1200) + "…" : text;
    }
}
