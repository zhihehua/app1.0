package com.example.NoteMind;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AtomSummaryActivity extends AppCompatActivity {

    private Spinner spinnerMain, spinnerSecond;
    private TextView tvAiContent;
    private Button btnStartSummary;
    private AlertDialog loadingDialog;
    
    private QuestionDao questionDao;
    private NoteMindAgent agent;
    private String selectedTag = "";

    // 内部映射：子标签 -> 二级大类 (复刻自 MyPdfActivity)
    private final Map<String, String> tagToL2Map = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_atom_summary);

        Constants.init(this);
        loadStrictL2Mapping();

        questionDao = new QuestionDao(this);
        agent = new NoteMindAgent();

        initViews();
        setupMainSpinner();
    }

    private void loadStrictL2Mapping() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("default_graph.txt"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;
                String[] parts = line.split("-");
                if (parts.length >= 2) {
                    String root = parts[0];
                    String l2Tag = Constants.MAIN_CATEGORIES.contains(root) ? parts[1] : root;
                    for (String part : parts) {
                        tagToL2Map.put(part, l2Tag);
                    }
                }
            }
            reader.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        spinnerMain = findViewById(R.id.spinner_main_category);
        spinnerSecond = findViewById(R.id.spinner_second_category);
        tvAiContent = findViewById(R.id.tv_ai_content);
        btnStartSummary = findViewById(R.id.btn_start_summary);

        btnStartSummary.setOnClickListener(v -> {
            if (selectedTag.isEmpty() || selectedTag.contains("暂无")) {
                Toast.makeText(this, "请先选择一个有效的分类标签", Toast.LENGTH_SHORT).show();
                return;
            }
            performDeepAnalysis();
        });
    }

    private void setupMainSpinner() {
        List<String> mains = Constants.MAIN_CATEGORIES;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mains);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMain.setAdapter(adapter);
        spinnerMain.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                updateSecondSpinner(mains.get(pos));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateSecondSpinner(String mainCategory) {
        List<String> allTagsInDb = questionDao.getAllUniqueTags();
        Set<String> l2Results = new HashSet<>();

        for (String tag : allTagsInDb) {
            if (mainCategory.equals(Constants.getParentCategory(tag))) {
                String l2 = tagToL2Map.get(tag);
                l2Results.add(l2 != null ? l2 : tag);
            }
        }
        
        // 特殊补全
        if (Constants.CAT_STUDY.equals(mainCategory)) {
            l2Results.add("408");
            l2Results.add("高等数学");
        }

        List<String> displayList = new ArrayList<>(l2Results);
        Collections.sort(displayList);
        if (displayList.isEmpty()) displayList.add("暂无有效标签");

        ArrayAdapter<String> sAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayList);
        sAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSecond.setAdapter(sAdapter);
        spinnerSecond.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedTag = displayList.get(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void performDeepAnalysis() {
        // 1. 聚合查询该二级分类下的所有笔记
        List<QuestionNote> allNotesInDb = questionDao.queryAll();
        List<QuestionNote> targetNotes = new ArrayList<>();

        for (QuestionNote note : allNotesInDb) {
            String tag = note.getTag();
            if ("408".equals(selectedTag)) {
                if (Constants.MEMBERS_408.contains(tag) || "408".equals(tag)) targetNotes.add(note);
            } else if (selectedTag.equals(tagToL2Map.get(tag)) || selectedTag.equals(tag)) {
                targetNotes.add(note);
            }
        }

        if (targetNotes.isEmpty()) {
            tvAiContent.setText("🔍 标签 [" + selectedTag + "] 下暂无原子碎片。请记录后再试。");
            return;
        }

        // 2. 调用 Agent 进行灵魂复盘
        agent.requestSoulmateThinking(selectedTag, targetNotes, new NoteMindAgent.AgentCallback() {
            @Override
            public void onStart() {
                showLoading("NoteMind 正在深度思考 [" + selectedTag + "]...");
                runOnUiThread(() -> tvAiContent.setText("正在分析你的笔记碎影，请稍候..."));
            }

            @Override public void onIntentDetected(NoteMindAgent.NoteMindIntent intent) {}
            @Override public void onProgress(String chunk) {}

            @Override
            public void onSuccess(String finalResult) {
                hideLoading();
                runOnUiThread(() -> tvAiContent.setText(finalResult));
            }

            @Override
            public void onError(String error) {
                hideLoading();
                runOnUiThread(() -> tvAiContent.setText("⚠️ 思考中断：" + error));
            }
        });
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || (loadingDialog != null && loadingDialog.isShowing())) return;
            View v = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
            TextView tvMsg = v.findViewById(R.id.loading_text);
            if (tvMsg != null) tvMsg.setText(message);
            loadingDialog = new MaterialAlertDialogBuilder(this).setView(v).setCancelable(false).create();
            loadingDialog.show();
            if (loadingDialog.getWindow() != null) loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (questionDao != null) questionDao.close();
        hideLoading();
    }
}
