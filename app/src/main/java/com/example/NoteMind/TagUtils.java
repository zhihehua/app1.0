package com.example.NoteMind;

import java.util.ArrayList;
import java.util.List;

public class TagUtils {
    /**
     * 返回固定的顶级分类列表
     */
    public static List<String> groupTags(List<String> rawTags) {
        // 不再根据数据库内容动态生成，而是直接返回定义的四大分类
        return new ArrayList<>(Constants.MAIN_CATEGORIES);
    }
}
