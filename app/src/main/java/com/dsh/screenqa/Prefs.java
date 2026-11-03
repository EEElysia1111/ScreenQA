package com.dsh.screenqa;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String NAME = "screenqa";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String apiKey(Context context) {
        return get(context).getString("api_key", "");
    }

    public static String model(Context context) {
        return get(context).getString("model", "deepseek-chat");
    }

    public static String reasonerModel(Context context) {
        return get(context).getString("reasoner_model", "deepseek-reasoner");
    }

    public static String baseUrl(Context context) {
        return get(context).getString("base_url", "https://api.deepseek.com");
    }

    public static boolean hasKey(Context context) {
        return !apiKey(context).trim().isEmpty();
    }

    public static void save(Context context, String key, String model, String baseUrl) {
        save(context, key, model, baseUrl, "deepseek-reasoner");
    }

    public static void save(Context context, String key, String model, String baseUrl, String reasonerModel) {
        get(context).edit()
                .putString("api_key", key == null ? "" : key.trim())
                .putString("model", model == null || model.trim().isEmpty() ? "deepseek-chat" : model.trim())
                .putString("base_url", baseUrl == null || baseUrl.trim().isEmpty() ? "https://api.deepseek.com" : baseUrl.trim())
                .putString("reasoner_model", reasonerModel == null || reasonerModel.trim().isEmpty()
                        ? "deepseek-reasoner" : reasonerModel.trim())
                .apply();
    }
}
