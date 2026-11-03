package com.dsh.screenqa;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Local text knowledge base:
 * - lines like "题目|答案" or "题目<TAB>答案" are treated as a question bank
 * - everything is also chunked for lightweight keyword retrieval (RAG)
 * Files live in Android/data/com.dsh.screenqa/files/kb/*.txt
 */
public final class LocalKnowledge {

    private static final Object LOCK = new Object();
    private static final List<Qa> QAS = new ArrayList<>();
    private static final List<String> CHUNKS = new ArrayList<>();
    private static String signature = "";
    private static String status = "未加载";

    private LocalKnowledge() {
    }

    public static void refresh(Context context) {
        synchronized (LOCK) {
            load(context);
        }
    }

    public static String status(Context context) {
        ensure(context);
        synchronized (LOCK) {
            return status;
        }
    }

    public static String match(Context context, String question) {
        ensure(context);
        String target = normalize(question);
        if (target.length() < 5) {
            return null;
        }
        Qa best = null;
        double bestScore = 0;
        synchronized (LOCK) {
            for (Qa qa : QAS) {
                double score = similarity(target, normalize(qa.question));
                if (score > bestScore) {
                    bestScore = score;
                    best = qa;
                }
            }
        }
        return best != null && bestScore >= 0.72 ? best.answer : null;
    }

    public static String retrieve(Context context, String question, int maxChunks, int maxChars) {
        ensure(context);
        List<String> chunks;
        synchronized (LOCK) {
            chunks = new ArrayList<>(CHUNKS);
        }
        if (chunks.isEmpty()) {
            return "";
        }
        Set<String> queryGrams = grams(normalize(question));
        if (queryGrams.isEmpty()) {
            return "";
        }
        String normalizedQuestion = normalize(question);
        List<Scored> scored = new ArrayList<>();
        for (String chunk : chunks) {
            Set<String> chunkGrams = grams(chunk);
            int intersection = 0;
            for (String gram : queryGrams) {
                if (chunkGrams.contains(gram)) {
                    intersection++;
                }
            }
            double score = intersection / Math.sqrt(chunkGrams.size() + 1.0);
            if (!normalizedQuestion.isEmpty() && normalize(chunk).contains(normalizedQuestion)) {
                score += 10;
            }
            if (score > 0) {
                scored.add(new Scored(chunk, score));
            }
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int i = 0; i < scored.size() && i < maxChunks; i++) {
            String chunk = scored.get(i).text;
            if (builder.length() > 0) {
                builder.append("\n---\n");
            }
            builder.append(chunk);
            used += chunk.length();
            if (used >= maxChars) {
                break;
            }
        }
        return builder.toString();
    }

    private static void ensure(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "kb");
        String current = signature(dir);
        synchronized (LOCK) {
            if (current.equals(signature)) {
                return;
            }
            load(context);
        }
    }

    private static void load(Context context) {
        QAS.clear();
        CHUNKS.clear();
        File dir = new File(context.getExternalFilesDir(null), "kb");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File[] files = dir.listFiles();
        StringBuilder all = new StringBuilder();
        long total = 0;
        if (files != null) {
            for (File file : files) {
                if (!isText(file) || file.length() > 2_000_000L) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    String pendingQuestion = null;
                    while ((line = reader.readLine()) != null) {
                        if (total > 5_000_000L) {
                            break;
                        }
                        all.append(line).append('\n');
                        total += line.length() + 1;
                        String value = line.trim();
                        if (value.isEmpty()) {
                            continue;
                        }
                        String[] pair = splitQa(value);
                        if (pair != null) {
                            QAS.add(new Qa(pair[0], pair[1]));
                            pendingQuestion = null;
                            continue;
                        }
                        if (value.startsWith("Q:") || value.startsWith("Q：")
                                || value.startsWith("问:") || value.startsWith("问：")) {
                            pendingQuestion = afterColon(value);
                        } else if (pendingQuestion != null
                                && (value.startsWith("A:") || value.startsWith("A：")
                                || value.startsWith("答:") || value.startsWith("答："))) {
                            String answer = afterColon(value);
                            if (pendingQuestion.length() >= 5 && !answer.isEmpty()) {
                                QAS.add(new Qa(pendingQuestion, answer));
                            }
                            pendingQuestion = null;
                        }
                    }
                } catch (Exception e) {
                    Log.e("ScreenQA", "read kb file failed", e);
                }
            }
        }
        chunk(all.toString());
        signature = signature(dir);
        status = QAS.size() + " 题 / " + CHUNKS.size() + " 段";
    }

    private static String afterColon(String value) {
        int index = value.indexOf(':');
        if (index < 0) {
            index = value.indexOf('：');
        }
        return index >= 0 ? value.substring(index + 1).trim() : value;
    }

    private static String[] splitQa(String line) {
        String[] delimiters = {"\t", "|", "｜", "=>", "->"};
        for (String delimiter : delimiters) {
            int index = line.indexOf(delimiter);
            if (index > 5) {
                String question = line.substring(0, index).trim();
                String answer = line.substring(index + delimiter.length()).trim();
                if (question.length() >= 5 && !answer.isEmpty()) {
                    return new String[]{question, answer};
                }
            }
        }
        int answerIndex = line.indexOf("答案：");
        if (answerIndex < 0) {
            answerIndex = line.indexOf("答案:");
        }
        if (answerIndex > 5) {
            String question = line.substring(0, answerIndex).trim();
            String answer = line.substring(answerIndex + 3).trim();
            if (question.length() >= 5 && !answer.isEmpty()) {
                return new String[]{question, answer};
            }
        }
        return null;
    }

    private static void chunk(String text) {
        String normalized = text
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        int size = 500;
        int overlap = 80;
        for (int start = 0; start < normalized.length(); start += (size - overlap)) {
            int end = Math.min(normalized.length(), start + size);
            CHUNKS.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
    }

    private static boolean isText(File file) {
        String name = file.getName().toLowerCase();
        return file.isFile() && (name.endsWith(".txt") || name.endsWith(".md"));
    }

    private static String signature(File dir) {
        if (!dir.exists()) {
            return "none";
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (File file : files) {
            if (isText(file)) {
                builder.append(file.getName()).append(':')
                        .append(file.length()).append(':')
                        .append(file.lastModified()).append(';');
            }
        }
        return builder.toString();
    }

    private static double similarity(String a, String b) {
        Set<String> gramsA = grams(a);
        Set<String> gramsB = grams(b);
        if (gramsA.isEmpty() || gramsB.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String gram : gramsA) {
            if (gramsB.contains(gram)) {
                intersection++;
            }
        }
        double score = intersection / (double) (gramsA.size() + gramsB.size() - intersection);
        if (a.contains(b) || b.contains(a)) {
            score = Math.max(score, 0.8);
        }
        return score;
    }

    private static Set<String> grams(String text) {
        String value = normalize(text);
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < value.length(); i++) {
            set.add(value.substring(i, i + 2));
        }
        if (set.isEmpty() && !value.isEmpty()) {
            set.add(value);
        }
        return set;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）《》【】…—·]", "");
    }

    private static final class Qa {
        final String question;
        final String answer;

        Qa(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    private static final class Scored {
        final String text;
        final double score;

        Scored(String text, double score) {
            this.text = text;
            this.score = score;
        }
    }
}
