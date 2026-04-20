package com.example.NoteMind;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * NoteMind 智核代理 - 负责与服务器通信
 * 已恢复所有方法，解决 MainActivity 报错。
 */
public class NoteMindAgent {
    private static final String TAG = "NoteMindAgent";
    
    private static final String API_URL = "http://8.156.90.48:5000/api/ai";
    private static final String MODEL = "doubao-seed-1-8-251228";

    public enum NoteMindIntent { SUMMARIZE, MIND_MAP, REVIEW, UNKNOWN }

    public interface AgentCallback {
        void onStart();
        void onIntentDetected(NoteMindIntent intent);
        void onProgress(String chunk);
        void onSuccess(String finalResult);
        void onError(String error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 【恢复】MainActivity 调用的 AI 文本对话方法
     */
    public void requestAiText(String text, AgentCallback callback) {
        new Thread(() -> {
            try {
                mainHandler.post(callback::onStart);
                JSONObject json = new JSONObject();
                json.put("model", MODEL);
                json.put("stream", true);
                
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", getSystemPromptWithContext()));
                messages.put(new JSONObject().put("role", "user").put("content", text));
                
                json.put("messages", messages);
                callApiStreaming(json.toString(), callback);
            } catch (Exception e) {
                postError(callback, "初始化失败");
            }
        }).start();
    }

    /**
     * 【恢复】MainActivity 调用的 OCR 图片识别方法
     */
    public void requestAiOcr(Bitmap bitmap, String tag, AgentCallback callback) {
        new Thread(() -> {
            try {
                mainHandler.post(callback::onStart);
                String base64 = bitmapToSmallBase64(bitmap);
                JSONObject json = new JSONObject();
                json.put("model", MODEL);
                json.put("stream", true);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", getSystemPromptWithContext()));
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                JSONArray contentArr = new JSONArray();
                JSONObject textObj = new JSONObject();
                textObj.put("type", "text");
                textObj.put("text", tag.isEmpty() ? "识别并分类图片内容。" : "【场景：" + tag + "】分析图片。");
                contentArr.put(textObj);
                JSONObject imgObj = new JSONObject();
                imgObj.put("type", "image_url");
                imgObj.put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + base64));
                contentArr.put(imgObj);
                msg.put("content", contentArr);
                messages.put(msg);
                json.put("messages", messages);
                callApiStreaming(json.toString(), callback);
            } catch (Exception e) {
                postError(callback, "图片处理失败");
            }
        }).start();
    }

    /**
     * 【深度灵魂复盘】
     */
    public void requestSoulmateThinking(String tagName, List<QuestionNote> notes, AgentCallback callback) {
        new Thread(() -> {
            try {
                mainHandler.post(callback::onStart);
                StringBuilder notesContext = new StringBuilder();
                for (QuestionNote note : notes) {
                    notesContext.append("- [").append(note.getCategory()).append("] ").append(note.getQuestion()).append("\n");
                }
                JSONObject json = new JSONObject();
                json.put("model", MODEL);
                json.put("stream", true);
                String systemRole = "你是一个灵魂伴侣式知识管家。你需要对用户关于【" + tagName + "】的笔记进行深度洞察，指出其思维盲区，并给出具体的破局打算。语气要睿智且像老友。禁止Markdown，禁止废话，300字内。";
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", systemRole));
                messages.put(new JSONObject().put("role", "user").put("content", "这是我的笔记记录：\n" + notesContext.toString()));
                json.put("messages", messages);
                callApiStreaming(json.toString(), callback);
            } catch (Exception e) {
                postError(callback, "思考引擎启动异常");
            }
        }).start();
    }

    private void callApiStreaming(String body, AgentCallback callback) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000); 

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
            }

            if (conn.getResponseCode() != 200) {
                postError(callback, "服务异常: " + conn.getResponseCode());
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            StringBuilder fullResponse = new StringBuilder();
            boolean intentSent = false;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if (data.equals("[DONE]")) break;
                    try {
                        JSONObject chunkJson = new JSONObject(data);
                        JSONObject delta = chunkJson.getJSONArray("choices").getJSONObject(0).getJSONObject("delta");
                        if (delta.has("content")) {
                            String content = delta.getString("content");
                            fullResponse.append(content);
                            if (!intentSent && fullResponse.length() < 80) {
                                if (fullResponse.toString().contains("SUMMARIZE")) {
                                    intentSent = true;
                                    mainHandler.post(() -> callback.onIntentDetected(NoteMindIntent.SUMMARIZE));
                                } else if (fullResponse.toString().contains("MIND_MAP")) {
                                    intentSent = true;
                                    mainHandler.post(() -> callback.onIntentDetected(NoteMindIntent.MIND_MAP));
                                }
                            }
                            mainHandler.post(() -> callback.onProgress(content));
                        }
                    } catch (Exception ignored) {}
                }
            }
            mainHandler.post(() -> callback.onSuccess(cleanOutput(fullResponse.toString())));
        } catch (Exception e) {
            postError(callback, "异常: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String getSystemPromptWithContext() {
        return "你是一个知识助手。请尽量按此分类建议归类：学习[408,高数],生活[健康,菜谱],工作[待办,项目]。\n" +
                "1. 首行必须输出 INTENT:SUMMARIZE 或 INTENT:MIND_MAP。\n" +
                "2. 总结格式：题目、解答、标签、分类。";
    }

    private String cleanOutput(String raw) {
        return raw.replaceAll("(?i)INTENT:.*\\n?", "").replaceAll("```[a-zA-Z]*\\n?", "").replace("```", "").trim();
    }

    private String bitmapToSmallBase64(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = Math.min(1000f / width, 1000f / height);
        if (ratio < 1.0) {
            width = (int) (width * ratio);
            height = (int) (height * ratio);
        }
        Bitmap small = Bitmap.createScaledBitmap(bitmap, width, height, true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private void postError(AgentCallback callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }
}
