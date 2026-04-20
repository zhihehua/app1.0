package com.example.NoteMind;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ManualInputActivity extends AppCompatActivity {

    private EditText et_question, et_answer, et_tag, et_category, et_user_note;
    private GridLayout gl_photos;
    private ImageButton btn_photo, btn_gallery;
    private TextView btnAddTag, btnAddCategory, tvCurrentScene;
    private QuestionDao dao;
    private ArrayList<String> photoPathList = new ArrayList<>();
    private String currentPhotoPath;
    private String currentMainScene = ""; 

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_GALLERY = 101;
    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_input);

        Constants.init(this);
        dao = new QuestionDao(this);

        initViews();
        setupListeners();
        
        // 初始填充逻辑
        String preTag = getIntent().getStringExtra("PRE_TAG");
        if (preTag != null && !preTag.isEmpty()) {
            et_tag.setText(preTag);
            currentMainScene = Constants.getParentCategory(preTag);
            updateSceneUI();
        }
    }

    private void initViews() {
        et_question = findViewById(R.id.et_question_manual);
        et_answer = findViewById(R.id.et_answer_manual);
        et_tag = findViewById(R.id.et_tag_manual);
        et_category = findViewById(R.id.et_category_manual);
        et_user_note = findViewById(R.id.et_user_note_manual);
        gl_photos = findViewById(R.id.gl_photos_manual);

        btn_photo = findViewById(R.id.btn_photo_manual);
        btn_gallery = findViewById(R.id.btn_gallery_manual);
        btnAddTag = findViewById(R.id.btn_add_tag_manual);
        btnAddCategory = findViewById(R.id.btn_add_category_manual);
        tvCurrentScene = findViewById(R.id.tv_current_scene_manual);
    }

    private void setupListeners() {
        btn_photo.setOnClickListener(v -> takePhoto());
        btn_gallery.setOnClickListener(v -> openGallery());
        
        tvCurrentScene.setOnClickListener(v -> showSceneSelectionDialog());
        btnAddTag.setOnClickListener(v -> showTagSelectionDialog());
        btnAddCategory.setOnClickListener(v -> {
            String selectedTag = et_tag.getText().toString().trim();
            if (selectedTag.isEmpty()) {
                Toast.makeText(this, "请先选定一级标签", Toast.LENGTH_SHORT).show();
                showTagSelectionDialog();
            } else {
                showRecursiveCategorySelection(selectedTag);
            }
        });

        findViewById(R.id.btn_save_manual).setOnClickListener(v -> saveNote());
        findViewById(R.id.btn_cancel_manual).setOnClickListener(v -> finish());
    }

    private void updateSceneUI() {
        String displayText = currentMainScene.isEmpty() ? "未选定" : currentMainScene;
        tvCurrentScene.setText("当前场景: [" + displayText + "]");
    }

    private void showSceneSelectionDialog() {
        String[] mainTags = Constants.MAIN_CATEGORIES.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("切换当前聚焦场景")
                .setItems(mainTags, (dialog, which) -> {
                    currentMainScene = mainTags[which];
                    updateSceneUI();
                    et_tag.setText("");
                    et_category.setText("");
                    showTagSelectionDialog();
                })
                .show();
    }

    private void showTagSelectionDialog() {
        if (currentMainScene.isEmpty()) {
            Toast.makeText(this, "请先选定主场景", Toast.LENGTH_SHORT).show();
            showSceneSelectionDialog();
            return;
        }

        List<String> tags = dao.getCategoriesByTag(currentMainScene);
        if (Constants.CAT_STUDY.equals(currentMainScene) && tags.isEmpty()) {
            tags = Constants.STUDY_ROOTS;
        }

        if (tags.isEmpty()) {
            Toast.makeText(this, "该场景下暂无预设标签，请手动输入", Toast.LENGTH_SHORT).show();
            et_tag.requestFocus();
            return;
        }

        String[] tagArray = tags.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择 [" + currentMainScene + "] 下的标签")
                .setItems(tagArray, (dialog, which) -> {
                    String selected = tagArray[which];
                    et_tag.setText(selected);
                    et_category.setText("");
                    showRecursiveCategorySelection(selected);
                })
                .show();
    }

    private void showRecursiveCategorySelection(String parentNode) {
        List<String> children = dao.getCategoriesByTag(parentNode);
        if (children.isEmpty() && Constants.TAG_408.equals(parentNode)) {
            children = Constants.MEMBERS_408;
        }

        if (children.isEmpty()) {
            Toast.makeText(this, "该项下暂无细分，请手动输入", Toast.LENGTH_SHORT).show();
            et_category.requestFocus();
            return;
        }

        String[] options = children.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择 [" + parentNode + "] 的细分")
                .setItems(options, (dialog, which) -> {
                    String selectedItem = options[which];
                    boolean hasSub = dao.isRelationExistsAsParent(selectedItem) || Constants.TAG_408.equals(selectedItem);

                    if (hasSub) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("层级选项: " + selectedItem)
                                .setMessage("该项下还有详细分类，您要：")
                                .setPositiveButton("继续深入", (d, w) -> {
                                    et_tag.setText(selectedItem);
                                    et_category.setText("");
                                    showRecursiveCategorySelection(selectedItem);
                                })
                                .setNegativeButton("就选这个", (d, w) -> et_category.setText(selectedItem))
                                .show();
                    } else {
                        et_category.setText(selectedItem);
                    }
                })
                .setNeutralButton("手动输入", (dialog, which) -> et_category.requestFocus())
                .show();
    }

    private void takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try { photoFile = createImageFile(); } catch (IOException ignored) {}
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(this, "com.example.NoteMind.fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA && currentPhotoPath != null) {
                photoPathList.add(currentPhotoPath);
                addImageToView(currentPhotoPath);
            } else if (requestCode == REQUEST_GALLERY && data != null) {
                Uri uri = data.getData();
                String path = getImagePathFromUri(uri);
                if (path != null) {
                    photoPathList.add(path);
                    addImageToView(path);
                }
            }
        }
    }

    private String getImagePathFromUri(Uri uri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null) {
            int col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(col);
            cursor.close();
            return path;
        }
        return null;
    }

    private void addImageToView(String path) {
        ImageView iv = new ImageView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 240; params.height = 240; params.setMargins(8, 8, 8, 8);
        iv.setLayoutParams(params);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageURI(Uri.fromFile(new File(path)));
        iv.setOnClickListener(v -> {
            Intent intent = new Intent(this, ImagePreviewActivity.class);
            intent.putExtra("path", path);
            startActivity(intent);
        });
        iv.setOnLongClickListener(v -> {
            photoPathList.remove(path);
            gl_photos.removeView(iv);
            return true;
        });
        gl_photos.addView(iv);
    }

    private void saveNote() {
        String q = et_question.getText().toString().trim();
        String a = et_answer.getText().toString().trim();
        String t = et_tag.getText().toString().trim();
        String c = et_category.getText().toString().trim();
        String n = et_user_note.getText().toString().trim();

        if (TextUtils.isEmpty(q) || TextUtils.isEmpty(t) || TextUtils.isEmpty(c)) {
            Toast.makeText(this, "必填项(标题/标签/细分)不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < photoPathList.size(); i++) {
            sb.append(photoPathList.get(i));
            if (i < photoPathList.size() - 1) sb.append(";");
        }
        String paths = sb.toString();

        // 修复点：传入 8 个参数，新增数据 id 传 0
        QuestionNote note = new QuestionNote(0, q, a, t, n, c, 0, paths);
        if (dao.addNote(note) > 0) {
            Toast.makeText(this, "录入成功", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dao != null) dao.close();
    }
}
