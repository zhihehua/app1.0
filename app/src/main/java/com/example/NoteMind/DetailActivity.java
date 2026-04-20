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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    private EditText et_question, et_answer, et_tag, et_category, et_user_note;
    private GridLayout gl_photos;
    private ImageButton btn_photo_note, btn_gallery_note;
    private TextView btnAddTag, btnAddCategory, tvCurrentScene;
    private QuestionDao dao;
    private int currentId = -1;
    private ArrayList<String> photoPathList = new ArrayList<>();
    private String currentPhotoPath;
    private String currentMainScene = ""; // 记录当前选择的四大主场景

    private static final int REQUEST_CAMERA = 100;
    private static final int REQUEST_GALLERY = 101;
    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString("temp_path");
        }

        et_question = findViewById(R.id.et_question);
        et_answer = findViewById(R.id.et_answer);
        et_tag = findViewById(R.id.et_tag);
        et_category = findViewById(R.id.et_category);
        et_user_note = findViewById(R.id.et_user_note);
        gl_photos = findViewById(R.id.gl_photos);

        btn_photo_note = findViewById(R.id.btn_photo_note);
        btn_gallery_note = findViewById(R.id.btn_gallery_note);
        btnAddTag = findViewById(R.id.btn_add_tag_detail);
        btnAddCategory = findViewById(R.id.btn_add_category_detail);
        tvCurrentScene = findViewById(R.id.tv_current_scene_detail);

        dao = new QuestionDao(this);

        btn_photo_note.setOnClickListener(v -> takePhoto());
        btn_gallery_note.setOnClickListener(v -> openGallery());
        
        // 同步确认页面的级联逻辑
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

        findViewById(R.id.btn_update).setOnClickListener(v -> updateNote());
        findViewById(R.id.btn_delete).setOnClickListener(v -> deleteNote());

        loadData();
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("temp_path", currentPhotoPath);
    }

    private void takePhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
            }

            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(this,
                        "com.example.NoteMind.fileprovider", photoFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(cameraIntent, REQUEST_CAMERA);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
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
            if (requestCode == REQUEST_CAMERA) {
                if (currentPhotoPath != null) {
                    photoPathList.add(currentPhotoPath);
                    addImageToView(currentPhotoPath);
                }
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
        String[] projection = {MediaStore.Images.Media.DATA};
        android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(columnIndex);
            cursor.close();
            return path;
        }
        return null;
    }

    private void addImageToView(String path) {
        ImageView iv = new ImageView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 240;
        params.height = 240;
        params.setMargins(8, 8, 8, 8);
        iv.setLayoutParams(params);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

        iv.setImageURI(Uri.fromFile(new File(path)));

        iv.setOnClickListener(v -> {
            Intent intent = new Intent(DetailActivity.this, ImagePreviewActivity.class);
            intent.putExtra("path", path);
            startActivity(intent);
        });

        iv.setOnLongClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要从该笔记中移除此图片吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        photoPathList.remove(path);
                        gl_photos.removeView(iv);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        gl_photos.addView(iv);
    }

    private void loadData() {
        currentId = getIntent().getIntExtra("id", -1);
        if (currentId == -1) return;

        QuestionNote note = dao.queryById(currentId);
        if (note == null) return;

        et_question.setText(note.getQuestion());
        et_answer.setText(note.getAnswer());
        et_tag.setText(note.getTag());
        et_category.setText(note.getCategory());
        et_user_note.setText(note.getUserNote());
        
        // 尝试通过 tag 反推主场景
        if (note.getTag() != null && !note.getTag().isEmpty()) {
            currentMainScene = Constants.getParentCategory(note.getTag());
        }
        updateSceneUI();

        String paths = note.getPhotoPaths();
        if (paths != null && !paths.isEmpty()) {
            String[] arr = paths.split(";");
            photoPathList = new ArrayList<>(Arrays.asList(arr));
            for (String p : arr) {
                if (new File(p).exists()) {
                    addImageToView(p);
                }
            }
        }
    }

    private void updateNote() {
        String question = et_question.getText().toString().trim();
        String answer = et_answer.getText().toString().trim();
        String tag = et_tag.getText().toString().trim();
        String category = et_category.getText().toString().trim();
        String userNote = et_user_note.getText().toString().trim();

        if (TextUtils.isEmpty(question) || TextUtils.isEmpty(tag) || TextUtils.isEmpty(category)) {
            Toast.makeText(this, "必填项不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String s : photoPathList) sb.append(s).append(";");
        String photoPaths = sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";

        QuestionNote note = new QuestionNote(currentId, question, answer, tag, userNote, category, 0, photoPaths);

        dao.updateNote(note);
        Toast.makeText(this, "修改已保存，新分类关系已记录", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void deleteNote() {
        dao.deleteById(currentId);
        Toast.makeText(this, "笔记已删除", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dao != null) dao.close();
    }
}
