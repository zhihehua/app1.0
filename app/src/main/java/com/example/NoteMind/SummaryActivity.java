package com.example.NoteMind;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public class SummaryActivity extends AppCompatActivity {
    private String targetTag;
    private QuestionDao dao;
    private TextView tvContent;
    private NoteMindAgent agent;
    private AlertDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        targetTag = getIntent().getStringExtra("TARGET_TAG");
        dao = new QuestionDao(this);
        agent = new NoteMindAgent(); 

        tvContent = findViewById(R.id.tv_ai_content);
        TextView tvTitle = findViewById(R.id.tv_summary_title);
        
        if (targetTag != null) {
            tvTitle.setText(targetTag + " · 灵魂复盘");
        }

        startDeepAnalysis();

        findViewById(R.id.btn_re_summary).setOnClickListener(v -> startDeepAnalysis());
    }

    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (isFinishing() || (loadingDialog != null && loadingDialog.isShowing())) return;
            
            View v = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
            TextView tvMsg = v.findViewById(R.id.loading_text);
            if (tvMsg != null) tvMsg.setText(message);

            loadingDialog = new MaterialAlertDialogBuilder(this)
                    .setView(v)
                    .setCancelable(false)
                    .create();
            
            loadingDialog.show();
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
    }

    private void hideLoading() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void startDeepAnalysis() {
        List<QuestionNote> notes = dao.queryByTag(targetTag);
        
        if (notes == null || notes.isEmpty()) {
            tvContent.setText("🔍 暂时没有发现关于 [" + targetTag + "] 的深度碎片。建议先去原子图谱记录一些内容。");
            return;
        }

        agent.requestSoulmateThinking(targetTag, notes, new NoteMindAgent.AgentCallback() {
            @Override
            public void onStart() {
                // 1. 弹出加载框，保持转圈
                showLoading("NoteMind 正在深度复盘中...");
                runOnUiThread(() -> tvContent.setText("正在连接思考，请稍候..."));
            }

            @Override
            public void onIntentDetected(NoteMindAgent.NoteMindIntent intent) {}

            @Override
            public void onProgress(String chunk) {
                // 需求要求：弹窗结束总结才出现，故此处不处理实时追加
            }

            @Override
            public void onSuccess(String finalResult) {
                // 2. 全部生成完毕，关闭弹窗
                hideLoading();
                // 3. 一次性显示文字
                runOnUiThread(() -> {
                    if (finalResult != null && !finalResult.isEmpty()) {
                        tvContent.setText(finalResult);
                    } else {
                        tvContent.setText("⚠️ 未获取到有效内容，请点击重新生成。");
                    }
                });
            }

            @Override
            public void onError(String error) {
                hideLoading();
                runOnUiThread(() -> tvContent.setText("⚠️ 思考中断：" + error));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideLoading();
        if (dao != null) dao.close();
    }
}
