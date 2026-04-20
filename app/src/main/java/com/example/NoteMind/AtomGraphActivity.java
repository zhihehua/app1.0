package com.example.NoteMind;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AtomGraphActivity extends AppCompatActivity {
    private WebView webView;
    private QuestionDao dao;
    private String targetTag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_atom_graph);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
        }

        targetTag = getIntent().getStringExtra("TARGET_TAG");
        if (targetTag == null) targetTag = "未知标签";

        TextView tvTitle = findViewById(R.id.tv_graph_title);
        tvTitle.setText(targetTag + " · 原子图谱");

        webView = findViewById(R.id.webview_atom);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        dao = new QuestionDao(this);

        initGraph();
        findViewById(R.id.btn_graph_back).setOnClickListener(v -> finish());
    }

    private void initGraph() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/atom_engine.html");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 确保页面加载完成后注入数据
                webView.postDelayed(() -> injectDataToGraph(), 300);
            }
        });
    }

    private void injectDataToGraph() {
        try {
            JSONObject data = new JSONObject();
            JSONArray nodes = new JSONArray();
            JSONArray links = new JSONArray();
            Set<String> addedNodes = new HashSet<>();

            // 1. 添加中心节点
            addNode(nodes, addedNodes, targetTag, 70, 0);

            // 2. 递归构建图谱数据
            buildGraphRecursively(targetTag, nodes, links, addedNodes, 1);

            data.put("nodes", nodes);
            data.put("links", links);

            final String jsonData = data.toString().replace("\\", "\\\\").replace("'", "\\'");
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    webView.evaluateJavascript("renderGraph('" + jsonData + "')", null);
                } else {
                    webView.loadUrl("javascript:renderGraph('" + jsonData + "')");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildGraphRecursively(String parentName, JSONArray nodes, JSONArray links, Set<String> addedNodes, int level) throws Exception {
        List<String> children = dao.getCategoriesByTag(parentName);
        
        // 补充预设层级
        if (children.isEmpty()) {
            if (Constants.CAT_STUDY.equals(parentName)) children = Constants.STUDY_ROOTS;
            else if (Constants.TAG_408.equals(parentName)) children = Constants.MEMBERS_408;
        }

        for (String child : children) {
            int value = Math.max(15, 50 - (level * 15));
            addNode(nodes, addedNodes, child, value, level);

            JSONObject link = new JSONObject();
            link.put("source", parentName);
            link.put("target", child);
            links.put(link);

            if (dao.isRelationExistsAsParent(child) || Constants.TAG_408.equals(child)) {
                buildGraphRecursively(child, nodes, links, addedNodes, level + 1);
            }
        }
    }

    private void addNode(JSONArray nodes, Set<String> addedNodes, String name, int value, int category) throws Exception {
        if (!addedNodes.contains(name)) {
            JSONObject node = new JSONObject();
            node.put("name", name);
            node.put("value", value);
            node.put("category", category);
            nodes.put(node);
            addedNodes.add(name);
        }
    }

    /**
     * JavaScript 交互桥梁
     */
    public class WebAppInterface {
        
        @android.webkit.JavascriptInterface
        public void openCategoryList(String categoryName) {
            runOnUiThread(() -> {
                boolean hasSub = dao.isRelationExistsAsParent(categoryName) || Constants.TAG_408.equals(categoryName);
                if (hasSub) {
                    Intent intent = new Intent(AtomGraphActivity.this, NoteListTwoActivity.class);
                    intent.putExtra("type", "tag");
                    intent.putExtra("keyword", categoryName);
                    startActivity(intent);
                } else {
                    List<QuestionNote> notes = dao.queryByTag(categoryName);
                    if (notes.size() == 1) {
                        Intent intent = new Intent(AtomGraphActivity.this, DetailActivity.class);
                        intent.putExtra("id", notes.get(0).getId());
                        startActivity(intent);
                    } else {
                        Intent intent = new Intent(AtomGraphActivity.this, NoteListTwoActivity.class);
                        intent.putExtra("type", "category");
                        intent.putExtra("keyword", categoryName);
                        startActivity(intent);
                    }
                }
            });
        }

        /**
         * 响应网页“个性化总结”按钮，跳转至 SummaryActivity
         */
        @android.webkit.JavascriptInterface
        public void startAiReview() {
            runOnUiThread(() -> {
                // 验证数据
                List<QuestionNote> notes = dao.queryByTag(targetTag);
                if (notes.isEmpty()) {
                    Toast.makeText(AtomGraphActivity.this, "当前标签下暂无笔记，无法进行知己总结", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 跳转
                Intent intent = new Intent(AtomGraphActivity.this, SummaryActivity.class);
                intent.putExtra("TARGET_TAG", targetTag);
                startActivity(intent);
                
                // 转场效果
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dao != null) dao.close();
    }
}
