package com.example.NoteMind;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NoteListActivity extends AppCompatActivity implements SensorEventListener {
    private EditText etSearch;
    private WebView webViewTags;
    private QuestionDao dao;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastUpdate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list);

        dao = new QuestionDao(this);
        etSearch = findViewById(R.id.et_search);
        webViewTags = findViewById(R.id.webview_tags);

        // 绑定返回按钮逻辑
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // 传感器初始化
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        initWebView();
        findViewById(R.id.btn_search).setOnClickListener(v -> showSearchTypeDialog());
    }

    private void initWebView() {
        webViewTags.setBackgroundColor(0); 
        webViewTags.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        webViewTags.getSettings().setJavaScriptEnabled(true);
        webViewTags.getSettings().setDomStorageEnabled(true);

        webViewTags.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void openTagList(String tagName) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(NoteListActivity.this, NoteListTwoActivity.class);
                    intent.putExtra("type", "tag");
                    intent.putExtra("keyword", tagName);
                    startActivity(intent);
                });
            }
        }, "AndroidBridge");

        webViewTags.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                refreshAtoms();
            }
        });
        webViewTags.loadUrl("file:///android_asset/tag_atoms.html");
    }

    /**
     * 核心逻辑修改：展示严格的二级标签
     * 严格二级标签定义：其父级必须是四大顶级分类（学习、生活、工作、其他）
     */
    private void refreshAtoms() {
        List<String> strictTags = new ArrayList<>();
        
        // 遍历四大顶级分类
        for (String mainCat : Constants.MAIN_CATEGORIES) {
            if (Constants.CAT_STUDY.equals(mainCat)) {
                // 针对“学习”大类，由于其子节点较多且层级深，这里严格展示其预设的核心入口（如 408、高等数学）
                strictTags.addAll(Constants.STUDY_ROOTS);
            } else {
                // 针对“生活/工作/其他”，直接从数据库查询以它们为直接父标签（Tag）的分类（Category）
                strictTags.addAll(dao.getCategoriesByTag(mainCat));
            }
        }
        
        // 使用 LinkedHashSet 去重并保持插入顺序
        Set<String> uniqueTags = new LinkedHashSet<>(strictTags);
        String json = new JSONArray(uniqueTags).toString();
        
        // 将计算好的严格二级标签传给 WebView 渲染
        webViewTags.evaluateJavascript("renderTags('" + json + "')", null);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long curTime = System.currentTimeMillis();
        if ((curTime - lastUpdate) > 16) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            webViewTags.post(() -> {
                webViewTags.evaluateJavascript("updateSensorData(" + x + "," + y + "," + z + ")", null);
            });
            lastUpdate = curTime;
        }
    }

    private void showSearchTypeDialog() {
        String text = etSearch.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] types = {"按标题", "按标签", "按分类"};
        new MaterialAlertDialogBuilder(this).setTitle("搜索类型").setItems(types, (d, w) -> {
            Intent intent = new Intent(this, NoteListTwoActivity.class);
            intent.putExtra("keyword", text);
            intent.putExtra("type", w == 0 ? "question" : (w == 1 ? "tag" : "category"));
            startActivity(intent);
        }).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onDestroy() { super.onDestroy(); dao.close(); }
}
