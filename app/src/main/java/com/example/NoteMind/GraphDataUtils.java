package com.example.NoteMind;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;

public class GraphDataUtils {
    private static final String TAG = "GraphDataUtils";
    private static final String PREFS_NAME = "NoteMindPrefs";
    private static final String HASH_KEY = "graph_file_hash";

    public interface OnDatabaseUpdatedListener {
        void onDatabaseUpdated();
    }

    /**
     * 增量同步默认图谱数据
     * 严密对齐：确保每个层级节点都能正确挂载并作为父节点被查询
     */
    public static void syncDefaultGraph(Context context, QuestionDao questionDao, OnDatabaseUpdatedListener listener) {
        new Thread(() -> {
            try {
                String currentHash = getFileHash(context, "default_graph.txt");
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String lastHash = prefs.getString(HASH_KEY, "");

                if (!currentHash.equals(lastHash)) {
                    Log.d(TAG, "检测到 default_graph.txt 内容变更，开始增量同步...");
                    
                    InputStream is = context.getAssets().open("default_graph.txt");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    String line;
                    boolean hasNewData = false;

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // 过滤掉注释和装饰线
                        if (line.isEmpty() || line.startsWith("//") || line.startsWith("=")) continue;

                        if (line.contains("-")) {
                            String[] parts = line.split("-");
                            // 严密遍历：生活-采购清单-数码
                            for (int i = 0; i < parts.length - 1; i++) {
                                String parent = parts[i].trim();
                                String child = parts[i + 1].trim();
                                
                                if (!parent.isEmpty() && !child.isEmpty()) {
                                    // 核心修正：
                                    // 检查是否存在这层父子关系 (tag = parent, category = child)
                                    if (!questionDao.isRelationExists(child, parent)) {
                                        // 填充逻辑：
                                        // 1. Question = child (节点名)
                                        // 2. Answer = "系统预设节点"
                                        // 3. Tag = parent (它的上级，作为索引)
                                        // 4. Category = child (它的分类，作为后续层级的父索引)
                                        // 5. UserNote = "系统预设" (用于区分用户和系统数据)
                                        QuestionNote note = new QuestionNote(child, "点击完善关于 [" + child + "] 的笔记内容", parent, "系统预设", child);
                                        questionDao.addNote(note);
                                        hasNewData = true;
                                        Log.d(TAG, "同步新节点: " + parent + " -> " + child);
                                    }
                                }
                            }
                        }
                    }
                    reader.close();

                    prefs.edit().putString(HASH_KEY, currentHash).apply();
                    
                    if (hasNewData && listener != null) {
                        listener.onDatabaseUpdated();
                    }
                    Log.d(TAG, "图谱增量同步完成");
                }
            } catch (Exception e) {
                Log.e(TAG, "同步 default_graph.txt 失败: " + e.getMessage());
            }
        }).start();
    }

    private static String getFileHash(Context context, String fileName) throws Exception {
        InputStream is = context.getAssets().open(fileName);
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        byte[] md5sum = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : md5sum) {
            sb.append(String.format("%02x", b));
        }
        is.close();
        return sb.toString();
    }
}
