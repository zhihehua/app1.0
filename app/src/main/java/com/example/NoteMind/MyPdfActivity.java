package com.example.NoteMind;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MyPdfActivity extends AppCompatActivity {

    private Spinner spinnerMain, spinnerSecond;
    private RecyclerView rvPreview;
    private View layoutEmpty;
    private Button btnConfirm;
    
    private QuestionDao questionDao;
    private List<QuestionNote> previewNotes = new ArrayList<>();
    private PreviewAdapter adapter;

    // 内部维护一个严格的二级归属映射：子标签 -> 二级大类
    private final Map<String, String> tagToL2Map = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_pdf);

        Constants.init(this);
        loadStrictL2Mapping(); // 加载严格的二级映射逻辑

        questionDao = new QuestionDao(this);
        initViews();
        setupMainSpinner();
    }

    /**
     * 解析 txt，建立 子标签 到 二级大类 的唯一映射
     */
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
                    String l2Tag;
                    // 如果第一级是四大类，则第二级是真正的 L2
                    if (Constants.MAIN_CATEGORIES.contains(root)) {
                        l2Tag = parts[1];
                        for (int i = 1; i < parts.length; i++) {
                            tagToL2Map.put(parts[i], l2Tag);
                        }
                    } else {
                        // 如果第一级不是四大类（如 数据结构-绪论），则第一级本身就是 L2
                        l2Tag = root;
                        for (String part : parts) {
                            tagToL2Map.put(part, l2Tag);
                        }
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        spinnerMain = findViewById(R.id.spinner_main_category);
        spinnerSecond = findViewById(R.id.spinner_second_category);
        rvPreview = findViewById(R.id.rv_preview);
        layoutEmpty = findViewById(R.id.layout_empty_preview);
        btnConfirm = findViewById(R.id.btn_confirm_export);

        rvPreview.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PreviewAdapter(previewNotes);
        rvPreview.setAdapter(adapter);

        btnConfirm.setOnClickListener(v -> {
            if (previewNotes.isEmpty()) {
                Toast.makeText(this, "没有内容可导出", Toast.LENGTH_SHORT).show();
                return;
            }
            performExport();
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
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void updateSecondSpinner(String mainCategory) {
        List<String> allTagsInDb = questionDao.getAllUniqueTags();
        Set<String> l2Results = new HashSet<>();

        for (String tag : allTagsInDb) {
            // 严密校验：该标签必须属于当前大类
            if (mainCategory.equals(Constants.getParentCategory(tag))) {
                // 获取它对应的二级大类入口
                String l2 = tagToL2Map.get(tag);
                if (l2 != null) {
                    l2Results.add(l2);
                } else {
                    // 如果 txt 里没定义，则它自己作为二级入口
                    l2Results.add(tag);
                }
            }
        }

        // 学习类补全入口
        if (Constants.CAT_STUDY.equals(mainCategory)) {
            l2Results.add("408");
            l2Results.add("高等数学");
        }

        List<String> displayList = new ArrayList<>(l2Results);
        Collections.sort(displayList);
        if (displayList.isEmpty()) displayList.add("暂无二级分类");

        ArrayAdapter<String> sAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayList);
        sAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSecond.setAdapter(sAdapter);
        spinnerSecond.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String sel = displayList.get(pos);
                if (!sel.contains("暂无")) loadPreviewDataByL2(sel);
                else clearPreview();
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    /**
     * 根据选定的二级大类，聚合导出所有属于该类及子类的笔记
     */
    private void loadPreviewDataByL2(String selectedL2) {
        List<QuestionNote> allNotes = questionDao.queryAll(); // 获取所有笔记
        previewNotes.clear();

        for (QuestionNote note : allNotes) {
            String tag = note.getTag();
            String l2 = tagToL2Map.get(tag);
            
            // 匹配逻辑：如果是 408 这种特殊聚合
            if ("408".equals(selectedL2)) {
                if (Constants.MEMBERS_408.contains(tag) || "408".equals(tag)) {
                    previewNotes.add(note);
                }
            } else if (selectedL2.equals(l2) || selectedL2.equals(tag)) {
                // 普通匹配：只要该笔记的二级上级是选中的入口，就加入预览
                previewNotes.add(note);
            }
        }

        if (previewNotes.isEmpty()) {
            clearPreview();
        } else {
            layoutEmpty.setVisibility(View.GONE);
            rvPreview.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
            rvPreview.scrollToPosition(0);
        }
    }

    private void clearPreview() {
        previewNotes.clear();
        adapter.notifyDataSetChanged();
        layoutEmpty.setVisibility(View.VISIBLE);
        rvPreview.setVisibility(View.GONE);
    }

    private void performExport() {
        new NotePdfExporter(this).exportNotes(previewNotes, new NotePdfExporter.ExportCallback() {
            @Override public void onStart() {}
            @Override
            public void onSuccess(String path) {
                Toast.makeText(MyPdfActivity.this, "导出成功", Toast.LENGTH_SHORT).show();
                clearPreview();
            }
            @Override public void onError(String e) {
                Toast.makeText(MyPdfActivity.this, "错误: " + e, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {
        private final List<QuestionNote> notes;
        public PreviewAdapter(List<QuestionNote> notes) { this.notes = notes; }
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_pdf_preview, p, false));
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            QuestionNote n = notes.get(p);
            if (h.tw1 != null) h.etTitle.removeTextChangedListener(h.tw1);
            if (h.tw2 != null) h.etAnswer.removeTextChangedListener(h.tw2);
            if (h.tw3 != null) h.etNote.removeTextChangedListener(h.tw3);
            h.etTitle.setText(n.getQuestion());
            h.etAnswer.setText(n.getAnswer());
            h.etNote.setText(n.getUserNote());
            h.tw1 = new SimpleWatcher(s -> n.setQuestion(s));
            h.tw2 = new SimpleWatcher(s -> n.setAnswer(s));
            h.tw3 = new SimpleWatcher(s -> n.setUserNote(s));
            h.etTitle.addTextChangedListener(h.tw1);
            h.etAnswer.addTextChangedListener(h.tw2);
            h.etNote.addTextChangedListener(h.tw3);
        }
        @Override public int getItemCount() { return notes.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            EditText etTitle, etAnswer, etNote;
            TextWatcher tw1, tw2, tw3;
            public ViewHolder(@NonNull View i) {
                super(i);
                etTitle = i.findViewById(R.id.et_preview_title);
                etAnswer = i.findViewById(R.id.et_preview_answer);
                etNote = i.findViewById(R.id.et_preview_note);
            }
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        private final java.util.function.Consumer<String> c;
        public SimpleWatcher(java.util.function.Consumer<String> c) { this.c = c; }
        public void beforeTextChanged(CharSequence s, int st, int co, int a) {}
        public void onTextChanged(CharSequence s, int st, int b, int co) {}
        public void afterTextChanged(Editable s) { c.accept(s.toString()); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (questionDao != null) questionDao.close();
    }
}
