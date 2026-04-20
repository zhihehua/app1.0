package com.example.NoteMind;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;
import java.util.ArrayList;

public class ConfirmActivity extends AppCompatActivity {
    private EditText etQuestion, etAnswer, etTag, etUserNote, etCategory;
    private Button btnCancel, btnSave;
    private TextView btnAddTag, btnAddCategory, tvCurrentScene;
    private QuestionDao dao;
    private String currentMainScene = ""; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm);

        Constants.init(this); // 确保标准库已加载
        dao = new QuestionDao(this);

        initViews();
        handleIncomingData();
    }

    private void initViews() {
        etQuestion = findViewById(R.id.et_question);
        etAnswer = findViewById(R.id.et_answer);
        etTag = findViewById(R.id.et_tag);
        etUserNote = findViewById(R.id.et_user_note);
        etCategory = findViewById(R.id.et_category);
        btnCancel = findViewById(R.id.btn_cancel);
        btnSave = findViewById(R.id.btn_save);
        btnAddTag = findViewById(R.id.btn_add_tag);
        btnAddCategory = findViewById(R.id.btn_add_category);
        tvCurrentScene = findViewById(R.id.tv_current_scene);

        tvCurrentScene.setOnClickListener(v -> showSceneSelectionDialog());
        btnAddTag.setOnClickListener(v -> showTagSelectionDialog());
        btnAddCategory.setOnClickListener(v -> {
            String selectedTag = etTag.getText().toString().trim();
            if (selectedTag.isEmpty()) {
                showTagSelectionDialog();
            } else {
                showRecursiveCategorySelection(selectedTag);
            }
        });
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> performSave());
    }

    /**
     * 【大改核心】处理 AI 传入的数据并进行模糊匹配
     */
    private void handleIncomingData() {
        String q = getIntent().getStringExtra("question");
        String a = getIntent().getStringExtra("answer");
        String rawTag = getIntent().getStringExtra("tag");
        String rawCategory = getIntent().getStringExtra("category");

        etQuestion.setText(q != null ? q : "");
        etAnswer.setText(a != null ? a : "");

        // 1. 自动对齐 Tag (L2 级)
        String matchedTag = Constants.matchStandardNode(rawTag);
        etTag.setText(matchedTag);

        // 2. 自动对齐 Category (细分级)
        String matchedCategory = Constants.matchStandardNode(rawCategory);
        etCategory.setText(matchedCategory);

        // 3. 自动反推并填充场景 (Main Scene)
        if (!matchedTag.isEmpty()) {
            currentMainScene = Constants.getParentCategory(matchedTag);
        } else if (!matchedCategory.isEmpty()) {
            currentMainScene = Constants.getParentCategory(matchedCategory);
        }
        
        updateSceneUI();
        
        if (!matchedTag.isEmpty() || !matchedCategory.isEmpty()) {
            Toast.makeText(this, "AI 已自动对齐本地知识库分类", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSceneUI() {
        String displayText = currentMainScene.isEmpty() ? "未识别到场景" : currentMainScene;
        tvCurrentScene.setText("当前场景: [" + displayText + "]");
    }

    private void showSceneSelectionDialog() {
        String[] mainTags = Constants.MAIN_CATEGORIES.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("手动校准场景")
                .setItems(mainTags, (dialog, which) -> {
                    currentMainScene = mainTags[which];
                    updateSceneUI();
                    etTag.setText("");
                    etCategory.setText("");
                    showTagSelectionDialog();
                })
                .show();
    }

    private void showTagSelectionDialog() {
        if (currentMainScene.isEmpty()) {
            showSceneSelectionDialog();
            return;
        }
        List<String> tags = dao.getCategoriesByTag(currentMainScene);
        if (Constants.CAT_STUDY.equals(currentMainScene) && tags.isEmpty()) {
            tags = Constants.STUDY_ROOTS;
        }
        String[] tagArray = tags.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("手动选择标签")
                .setItems(tagArray, (dialog, which) -> {
                    etTag.setText(tagArray[which]);
                    etCategory.setText("");
                })
                .show();
    }

    private void showRecursiveCategorySelection(String parentNode) {
        List<String> children = dao.getCategoriesByTag(parentNode);
        if (children.isEmpty() && Constants.TAG_408.equals(parentNode)) {
            children = Constants.MEMBERS_408;
        }
        if (children.isEmpty()) {
            etCategory.requestFocus();
            return;
        }
        String[] options = children.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("手动选择细分")
                .setItems(options, (dialog, which) -> etCategory.setText(options[which]))
                .show();
    }

    private void performSave() {
        String q = etQuestion.getText().toString().trim();
        String a = etAnswer.getText().toString().trim();
        String t = etTag.getText().toString().trim();
        String cate = etCategory.getText().toString().trim();
        String note = etUserNote.getText().toString().trim();

        if (TextUtils.isEmpty(q) || TextUtils.isEmpty(t) || TextUtils.isEmpty(cate) || TextUtils.isEmpty(currentMainScene)) {
            Toast.makeText(this, "场景/标签/分类 均不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        QuestionNote noteObj = new QuestionNote(q, a, t, note, cate);
        if (dao.addNote(noteObj) > 0) {
            Toast.makeText(this, "已对齐并保存至知识库", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dao.close();
    }
}
