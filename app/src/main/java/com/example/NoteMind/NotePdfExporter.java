package com.example.NoteMind;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.DashedLine;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NoteMind 专属 PDF 导出引擎
 * 负责处理笔记的结构化导出、图片压缩与中文字体适配
 */
public class NotePdfExporter {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AlertDialog loadingDialog;

    public interface ExportCallback {
        void onStart();
        void onSuccess(String filePath);
        void onError(String error);
    }

    public NotePdfExporter(Context context) {
        this.context = context;
    }

    public void exportNotes(List<QuestionNote> notes, ExportCallback callback) {
        if (notes == null || notes.isEmpty()) {
            if (callback != null) callback.onError("没有可导出的内容");
            return;
        }

        showLoading();
        if (callback != null) callback.onStart();

        executor.execute(() -> {
            String fileName = "NoteMind_Export_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".pdf";
            boolean success = false;
            String errorMsg = "";
            Uri fileUri = null;

            try {
                OutputStream outputStream;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    fileUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    outputStream = context.getContentResolver().openOutputStream(fileUri);
                } else {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    outputStream = new java.io.FileOutputStream(file);
                    fileUri = Uri.fromFile(file);
                }

                if (outputStream == null) throw new Exception("无法初始化文件输出流");

                PdfWriter writer = new PdfWriter(outputStream);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                document.setMargins(40, 40, 40, 40);

                // 1. 中文字体适配
                com.itextpdf.kernel.font.PdfFont chineseFont = loadChineseFont();
                if (chineseFont != null) document.setFont(chineseFont);

                // 2. 导出报头
                document.add(new Paragraph("NoteMind 知识库导出")
                        .setFontSize(24)
                        .setBold()
                        .setFontColor(ColorConstants.DARK_GRAY)
                        .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("生成时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()))
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.RIGHT));
                
                document.add(new LineSeparator(new SolidLine(1f)).setMarginBottom(20));

                // 3. 循环写入笔记内容
                for (QuestionNote note : notes) {
                    // 标题块
                    document.add(new Paragraph(safeText(note.getQuestion()))
                            .setFontSize(16)
                            .setBold()
                            .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                            .setPadding(6));

                    // 元数据
                    document.add(new Paragraph("分类: " + safeText(note.getCategory()) + " | 标签: " + safeText(note.getTag()))
                            .setFontSize(10)
                            .setFontColor(ColorConstants.GRAY)
                            .setMarginBottom(8));

                    // 正文 (解答内容)
                    document.add(new Paragraph("【解答】\n" + safeText(note.getAnswer()))
                            .setFontSize(12)
                            .setMultipliedLeading(1.5f));

                    // 用户心得
                    if (note.getUserNote() != null && !note.getUserNote().trim().isEmpty()) {
                        document.add(new Paragraph("【心得】\n" + safeText(note.getUserNote()))
                                .setFontSize(11)
                                .setFontColor(ColorConstants.DARK_GRAY)
                                .setItalic()
                                .setMarginTop(8));
                    }

                    // 图片处理
                    processImages(note.getPhotoPaths(), document);

                    document.add(new Paragraph("\n"));
                    document.add(new LineSeparator(new DashedLine(0.5f)).setMarginBottom(20));
                }

                document.close();
                outputStream.close();
                success = true;

                // 刷新系统媒体库
                if (fileUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scanIntent.setData(fileUri);
                    context.sendBroadcast(scanIntent);
                }

            } catch (Exception e) {
                e.printStackTrace();
                errorMsg = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMsg;
            final String finalPath = fileName;
            mainHandler.post(() -> {
                dismissLoading();
                if (finalSuccess) {
                    if (callback != null) callback.onSuccess(finalPath);
                } else {
                    if (callback != null) callback.onError(finalError);
                }
            });
        });
    }

    private com.itextpdf.kernel.font.PdfFont loadChineseFont() {
        String[] fontPaths = {
            "/system/fonts/NotoSansSC-Regular.otf", 
            "/system/fonts/NotoSansCJK-Regular.ttc,0", 
            "/system/fonts/DroidSansFallback.ttf"
        };
        for (String path : fontPaths) {
            try {
                String actualPath = path.contains(",") ? path.split(",")[0] : path;
                if (new File(actualPath).exists()) {
                    return com.itextpdf.kernel.font.PdfFontFactory.createFont(path, 
                            com.itextpdf.io.font.PdfEncodings.IDENTITY_H, 
                            com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void processImages(String paths, Document document) {
        if (paths == null || paths.isEmpty()) return;
        String[] pathArr = paths.split(";");
        for (String p : pathArr) {
            File imgFile = new File(p);
            if (imgFile.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2; // 降采样
                    Bitmap bmp = BitmapFactory.decodeFile(p, options);
                    if (bmp != null) {
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, stream);
                        ImageData imageData = ImageDataFactory.create(stream.toByteArray());
                        Image pdfImg = new Image(imageData);
                        pdfImg.setMaxWidth(400); 
                        pdfImg.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                        document.add(pdfImg);
                        bmp.recycle();
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    private String safeText(String input) {
        if (input == null) return "";
        // 过滤 PDF 不支持的特殊 Emoji 或控制字符
        return input.replaceAll("[^\\u4e00-\\u9fa5\\uFF00-\\uFFEF\\u0000-\\u007F\\s]", "").replace("\r", "");
    }

    private void showLoading() {
        mainHandler.post(() -> {
            // 使用专门设计的华丽简约 PDF 导出面板
            View v = LayoutInflater.from(context).inflate(R.layout.dialog_loading_pdf, null);
            TextView tv = v.findViewById(R.id.loading_text);
            if (tv != null) tv.setText("正在导出 PDF...");

            loadingDialog = new MaterialAlertDialogBuilder(context)
                    .setView(v)
                    .setCancelable(false)
                    .create();
            loadingDialog.show();
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
    }

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }
}
