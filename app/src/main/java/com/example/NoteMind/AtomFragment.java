package com.example.NoteMind;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class AtomFragment extends Fragment {

    private QuestionDao questionDao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tab_atom, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;
        questionDao = activity.getQuestionDao();

        TextView btnEnter = view.findViewById(R.id.btn_knowledge_atom_tab);
        btnEnter.getPaint().setFlags(Paint.UNDERLINE_TEXT_FLAG);
        btnEnter.getPaint().setAntiAlias(true);
        
        // 启动探索：从顶级分类开始
        btnEnter.setOnClickListener(v -> showRecursiveCategoryDialog("选择领域", "根节点", Constants.MAIN_CATEGORIES));
    }

    /**
     * 【重构核心】递归分类对话框
     * @param title 对话框标题
     * @param currentNode 当前节点名称
     * @param options 当前节点下的子选项列表
     */
    private void showRecursiveCategoryDialog(String title, String currentNode, List<String> options) {
        if (getContext() == null || options == null || options.isEmpty()) {
            // 如果点进来发现没子项了，直接展示当前节点图谱
            startGraphActivity(currentNode);
            return;
        }

        String[] itemArray = options.toArray(new String[0]);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(title)
                .setItems(itemArray, (dialog, which) -> {
                    String selectedItem = itemArray[which];
                    handleItemSelection(selectedItem);
                });

        // 如果不是最初的提示文字，则增加“选择当前标签”按钮
        if (!"根节点".equals(currentNode)) {
            builder.setPositiveButton("展示所有 [" + currentNode + "]", (dialog, which) -> {
                startGraphActivity(currentNode);
            });
            builder.setNeutralButton("返回", (dialog, which) -> {
                // 返回上一级：由于是递归，简单处理为返回顶级或根据需求记录路径
                showRecursiveCategoryDialog("选择领域", "根节点", Constants.MAIN_CATEGORIES);
            });
        } else {
            builder.setNegativeButton("取消", null);
        }

        builder.show();
    }

    /**
     * 处理具体项的选择，决定是深入还是直接展示
     */
    private void handleItemSelection(String selectedItem) {
        // 1. 获取子节点数据
        List<String> children = questionDao.getCategoriesByTag(selectedItem);
        
        // 2. 特殊逻辑补充：处理 408 或 学习类预设
        if (children.isEmpty()) {
            if (Constants.TAG_408.equals(selectedItem)) {
                children = Constants.MEMBERS_408;
            } else if (Constants.CAT_STUDY.equals(selectedItem)) {
                children = Constants.STUDY_ROOTS;
            }
        }

        if (children.isEmpty()) {
            // 真正没有子节点了，直接进入图谱
            startGraphActivity(selectedItem);
        } else {
            // 还有子节点，递归弹出下一级对话框，同时提供“选择当前”的机会
            showRecursiveCategoryDialog("选择 [" + selectedItem + "] 的子项", selectedItem, children);
        }
    }

    private void startGraphActivity(String tag) {
        if (tag == null || tag.equals("根节点")) return;
        Intent intent = new Intent(getActivity(), AtomGraphActivity.class);
        intent.putExtra("TARGET_TAG", tag);
        startActivity(intent);
    }
}
