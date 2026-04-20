package com.example.NoteMind;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class Constants {
    public static final String CAT_STUDY = "学习";
    public static final String CAT_LIFE = "生活";
    public static final String CAT_WORK = "工作";
    public static final String CAT_OTHER = "其他";

    public static final List<String> MAIN_CATEGORIES = Arrays.asList(CAT_STUDY, CAT_LIFE, CAT_WORK, CAT_OTHER);

    private static final Map<String, String> tagToParentMap = new HashMap<>();
    private static final Set<String> allStandardNodes = new HashSet<>(); 
    private static boolean isInitialized = false;

    public static final String TAG_408 = "408";
    public static final List<String> MEMBERS_408 = Arrays.asList("数据结构", "计算机组成原理", "操作系统", "计算机网络");
    public static final List<String> STUDY_ROOTS = Arrays.asList("408", "高等数学", "数据结构", "计算机组成原理", "操作系统", "计算机网络");

    public static void init(Context context) {
        if (isInitialized) return;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("default_graph.txt"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;

                String[] parts = line.split("-");
                if (parts.length >= 2) {
                    String root = parts[0];
                    String derivedRoot = deriveRoot(root);
                    for (String part : parts) {
                        tagToParentMap.put(part, derivedRoot);
                        allStandardNodes.add(part);
                    }
                }
            }
            reader.close();
            allStandardNodes.addAll(MAIN_CATEGORIES);
            isInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String deriveRoot(String firstNode) {
        if (MAIN_CATEGORIES.contains(firstNode)) return firstNode;
        if (firstNode.contains("数据结构") || firstNode.contains("计算机") || firstNode.contains("操作系统") || firstNode.contains("高等数学")) {
            return CAT_STUDY;
        }
        return CAT_OTHER;
    }

    /**
     * 【大改点】模糊匹配算法：寻找最接近的标准节点名称
     */
    public static String matchStandardNode(String input) {
        if (input == null || input.isEmpty()) return "";
        input = input.trim();

        // 1. 精确匹配
        if (allStandardNodes.contains(input)) return input;

        // 2. 包含匹配 (处理 "高数" -> "高等数学")
        for (String standard : allStandardNodes) {
            if (standard.contains(input) || input.contains(standard)) {
                return standard;
            }
        }

        // 3. 别名/语义硬编码补充
        if (input.equals("计网")) return "计算机网络";
        if (input.equals("计组")) return "计算机组成原理";
        if (input.equals("OS")) return "操作系统";
        if (input.equals("算法")) return "数据结构";

        return input; // 实在匹配不上，才返回原值
    }

    public static String getParentCategory(String tag) {
        if (tag == null) return CAT_OTHER;
        if (tagToParentMap.containsKey(tag)) return tagToParentMap.get(tag);
        
        // 语义兜底
        if (tag.contains("408") || tag.contains("数学") || tag.contains("数据结构")) return CAT_STUDY;
        if (tag.contains("采购") || tag.contains("生活")) return CAT_LIFE;
        return CAT_OTHER;
    }

    public static String getAllNodesSummary() {
        return allStandardNodes.toString();
    }
}
