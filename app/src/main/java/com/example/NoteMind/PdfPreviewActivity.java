package com.example.NoteMind;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PdfPreviewActivity extends AppCompatActivity {
    private RecyclerView rvPreview;
    private PreviewAdapter adapter;
    private static List<QuestionNote> staticNotes; // 简单起见使用静态变量传递大数据

    public static void startPreview(Context context, List<QuestionNote> notes) {
        staticNotes = new ArrayList<>(notes);
        context.startActivity(new android.content.Intent(context, PdfPreviewActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_preview);

        if (staticNotes == null) {
            finish();
            return;
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        rvPreview = findViewById(R.id.rv_preview);
        rvPreview.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PreviewAdapter(staticNotes);
        rvPreview.setAdapter(adapter);

        findViewById(R.id.btn_confirm_export).setOnClickListener(v -> {
            NotePdfExporter exporter = new NotePdfExporter(this);
            exporter.exportNotes(staticNotes, new NotePdfExporter.ExportCallback() {
                @Override public void onStart() {}
                @Override public void onSuccess(String filePath) {
                    Toast.makeText(PdfPreviewActivity.this, "导出成功！", Toast.LENGTH_LONG).show();
                    staticNotes = null;
                    finish();
                }
                @Override public void onError(String error) {
                    Toast.makeText(PdfPreviewActivity.this, "导出失败: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.ViewHolder> {
        private final List<QuestionNote> notes;

        public PreviewAdapter(List<QuestionNote> notes) {
            this.notes = notes;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pdf_preview, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            QuestionNote note = notes.get(position);
            holder.etTitle.setText(note.getQuestion());
            holder.etAnswer.setText(note.getAnswer());
            holder.etNote.setText(note.getUserNote());

            // 实时同步编辑后的内容
            holder.etTitle.addTextChangedListener(new SimpleTextWatcher(s -> note.setQuestion(s)));
            holder.etAnswer.addTextChangedListener(new SimpleTextWatcher(s -> note.setAnswer(s)));
            holder.etNote.addTextChangedListener(new SimpleTextWatcher(s -> note.setUserNote(s)));
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            EditText etTitle, etAnswer, etNote;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                etTitle = itemView.findViewById(R.id.et_preview_title);
                etAnswer = itemView.findViewById(R.id.et_preview_answer);
                etNote = itemView.findViewById(R.id.et_preview_note);
            }
        }
    }

    private interface OnTextChanged { void onChanged(String s); }
    private static class SimpleTextWatcher implements TextWatcher {
        private final OnTextChanged callback;
        public SimpleTextWatcher(OnTextChanged callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { callback.onChanged(s.toString()); }
    }
}
