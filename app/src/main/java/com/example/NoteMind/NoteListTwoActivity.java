package com.example.NoteMind;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteListTwoActivity extends AppCompatActivity {
    private LinearLayout container;
    private QuestionDao dao;
    private TextView tvEmpty, tvManage, tvTitle, tvBack;
    private LinearLayout llBatchOp;

    private String currentType, currentKeyword;
    private boolean isManaging = false;
    private List<QuestionNote> displayedNotes = new ArrayList<>();
    private Set<Integer> selectedIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list_two);

        container = findViewById(R.id.ll_note_list);
        tvEmpty = findViewById(R.id.tv_empty);
        tvManage = findViewById(R.id.tv_manage);
        tvBack = findViewById(R.id.tv_back);
        tvTitle = findViewById(R.id.tv_list_two_title);
        llBatchOp = findViewById(R.id.ll_batch_op);
        dao = new QuestionDao(this);

        currentType = getIntent().getStringExtra("type");
        currentKeyword = getIntent().getStringExtra("keyword");
        
        if (currentKeyword != null) {
            tvTitle.setText(currentKeyword);
        }

        // 返回事件：销毁当前层级，自动回到上一层
        tvBack.setOnClickListener(v -> finish());
        
        tvManage.setOnClickListener(v -> toggleManageMode());

        findViewById(R.id.btn_batch_delete).setOnClickListener(v -> performBatchDelete());

        findViewById(R.id.btn_batch_export).setOnClickListener(v -> {
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "请先勾选需要导出的笔记", Toast.LENGTH_SHORT).show();
                return;
            }
            List<QuestionNote> selectedNotes = new ArrayList<>();
            for (QuestionNote note : displayedNotes) {
                if (selectedIds.contains(note.getId())) {
                    selectedNotes.add(note);
                }
            }
            PdfPreviewActivity.startPreview(this, selectedNotes);
        });

        loadData();
    }

    private void loadData() {
        displayedNotes.clear();
        if (currentType == null || currentKeyword == null) {
            displayedNotes.addAll(dao.queryAll());
        } else {
            if ("tag".equals(currentType) || "category".equals(currentType)) {
                displayedNotes.addAll(dao.queryByTag(currentKeyword));
            } else if ("question".equals(currentType)) {
                List<QuestionNote> all = dao.queryAll();
                for (QuestionNote n : all) {
                    if (n.getQuestion().contains(currentKeyword)) {
                        displayedNotes.add(n);
                    }
                }
            }
        }
        renderList();
    }

    private void renderList() {
        container.removeAllViews();
        tvEmpty.setVisibility(displayedNotes.isEmpty() ? View.VISIBLE : View.GONE);
        tvManage.setVisibility(displayedNotes.isEmpty() ? View.GONE : View.VISIBLE);

        for (QuestionNote note : displayedNotes) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
            itemParams.setMargins(0, 10, 0, 10);
            item.setLayoutParams(itemParams);

            CheckBox cb = new CheckBox(this);
            cb.setVisibility(isManaging ? View.VISIBLE : View.GONE);
            cb.setChecked(selectedIds.contains(note.getId()));
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selectedIds.add(note.getId());
                else selectedIds.remove(note.getId());
            });

            TextView tv = new TextView(this);
            String questionText = note.getQuestion();
            boolean hasSub = dao.isRelationExistsAsParent(questionText);
            
            if (hasSub) {
                tv.setText(questionText + "  >");
            } else {
                tv.setText(questionText);
            }
            
            tv.setTextSize(16);
            tv.setBackgroundResource(R.drawable.dialog_bg);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            tvParams.setMargins(10, 0, 10, 0);
            tv.setLayoutParams(tvParams);
            tv.setPadding(40, 30, 40, 30);

            tv.setOnClickListener(v -> {
                if (isManaging) {
                    cb.setChecked(!cb.isChecked());
                } else {
                    if (hasSub) {
                        Intent intent = new Intent(this, NoteListTwoActivity.class);
                        intent.putExtra("type", "tag");
                        intent.putExtra("keyword", questionText);
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(this, DetailActivity.class).putExtra("id", note.getId()));
                    }
                }
            });

            item.addView(cb);
            item.addView(tv);
            container.addView(item);
        }
    }

    private void toggleManageMode() {
        isManaging = !isManaging;
        tvManage.setText(isManaging ? "取消" : "管理");
        llBatchOp.setVisibility(isManaging ? View.VISIBLE : View.GONE);
        selectedIds.clear();
        renderList();
    }

    private void performBatchDelete() {
        if (selectedIds.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除选中的笔记吗？此操作不可恢复。")
                .setPositiveButton("确定", (d, w) -> {
                    for (Integer id : selectedIds) dao.deleteById(id);
                    isManaging = false;
                    loadData();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        dao.close();
    }
}