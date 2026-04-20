package com.example.NoteMind;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

public class QuestionDao {
    private final DbHelper dbHelper;
    private final SQLiteDatabase db;

    public QuestionDao(Context context) {
        dbHelper = new DbHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    public long addNote(QuestionNote note) {
        ContentValues values = new ContentValues();
        values.put(DbHelper.QUESTION, note.getQuestion());
        values.put(DbHelper.ANSWER, note.getAnswer());
        values.put(DbHelper.TAG, note.getTag());
        values.put(DbHelper.USER_NOTE, note.getUserNote());
        values.put(DbHelper.CATEGORY, note.getCategory());
        values.put(DbHelper.KNOWLEDGE_ID, note.getKnowledgeId());
        values.put(DbHelper.PHOTO_PATHS, note.getPhotoPaths());
        return db.insert(DbHelper.TABLE_NAME, null, values);
    }

    public void deleteSystemPresets() {
        db.delete(DbHelper.TABLE_NAME, DbHelper.USER_NOTE + "=?", new String[]{"系统预设"});
    }

    public boolean isRelationExists(String tag, String category) {
        Cursor cursor = db.query(DbHelper.TABLE_NAME, null, 
                DbHelper.TAG + "=? AND " + DbHelper.CATEGORY + "=?", 
                new String[]{tag, category}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    /**
     * 检查某个名称是否作为 Tag 存在（即它是否有子节点/下一级）
     */
    public boolean isRelationExistsAsParent(String tagName) {
        if (Constants.TAG_408.equals(tagName)) {
            return false; // 408 is a virtual group for notes, not a parent in hierarchy
        }
        Cursor cursor = db.query(DbHelper.TABLE_NAME, null, 
                DbHelper.TAG + "=?", 
                new String[]{tagName}, null, null, null);
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public List<QuestionNote> queryAll() {
        List<QuestionNote> list = new ArrayList<>();
        Cursor cursor = db.query(DbHelper.TABLE_NAME, null, null, null, null, null, DbHelper.ID + " DESC");
        while (cursor.moveToNext()) {
            list.add(cursorToNote(cursor));
        }
        cursor.close();
        return list;
    }

    public List<String> getAllUniqueTags() {
        List<String> tags = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + DbHelper.TAG + " FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.TAG + " IS NOT NULL AND " + DbHelper.TAG + " != ''", null);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String tag = cursor.getString(cursor.getColumnIndex(DbHelper.TAG));
            tags.add(tag);
        }
        cursor.close();
        return tags;
    }

    /**
     * 获取顶级标签：即该标签从未作为 category 出现过（没有前置节点）
     */
    public List<String> getTopLevelTags() {
        List<String> tags = new ArrayList<>();
        String sql = "SELECT DISTINCT " + DbHelper.TAG + " FROM " + DbHelper.TABLE_NAME + 
                     " WHERE " + DbHelper.TAG + " IS NOT NULL AND " + DbHelper.TAG + " != '' " +
                     " AND " + DbHelper.TAG + " NOT IN (SELECT DISTINCT " + DbHelper.CATEGORY + 
                     " FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.CATEGORY + " IS NOT NULL AND " + DbHelper.CATEGORY + " != '')";
        Cursor cursor = db.rawQuery(sql, null);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String tag = cursor.getString(cursor.getColumnIndex(DbHelper.TAG));
            tags.add(tag);
        }
        cursor.close();
        return tags;
    }

    /**
     * 获取所有二级标签：即该标签有父级（Tag 不为空且作为 Category 出现过）
     */
    public List<String> getSecondLevelTags() {
        List<String> tags = new ArrayList<>();
        // 二级标签定义：它是某条记录的 Category，且该记录的 Tag 不为空
        String sql = "SELECT DISTINCT " + DbHelper.CATEGORY + " FROM " + DbHelper.TABLE_NAME + 
                     " WHERE " + DbHelper.CATEGORY + " IS NOT NULL AND " + DbHelper.CATEGORY + " != '' " +
                     " AND " + DbHelper.TAG + " IS NOT NULL AND " + DbHelper.TAG + " != ''";
        Cursor cursor = db.rawQuery(sql, null);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String tag = cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY));
            tags.add(tag);
        }
        cursor.close();
        return tags;
    }

    /**
     * 获取某个父标签下的所有直接子标签（即 category）
     */
    public List<String> getCategoriesByTag(String tag) {
        List<String> list = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + DbHelper.CATEGORY + " FROM " + DbHelper.TABLE_NAME + 
                                     " WHERE " + DbHelper.TAG + "=? AND " + DbHelper.CATEGORY + " IS NOT NULL AND " + DbHelper.CATEGORY + " != ''", new String[]{tag});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String cat = cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY));
            list.add(cat);
        }
        cursor.close();
        return list;
    }

    public List<String> getAllUniqueCategories() {
        List<String> categories = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT DISTINCT " + DbHelper.CATEGORY + " FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.CATEGORY + " IS NOT NULL AND " + DbHelper.CATEGORY + " != ''", null);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String category = cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY));
            categories.add(category);
        }
        cursor.close();
        return categories;
    }

    public List<QuestionNote> queryByTag(String tag) {
        if (Constants.TAG_408.equals(tag)) {
            List<QuestionNote> all = new ArrayList<>();
            for (String subTag : Constants.MEMBERS_408) {
                all.addAll(queryByTagInternal(subTag));
            }
            return all;
        }
        return queryByTagInternal(tag);
    }

    private List<QuestionNote> queryByTagInternal(String tag) {
        List<QuestionNote> list = new ArrayList<>();
        Cursor cursor = db.query(DbHelper.TABLE_NAME, null, DbHelper.TAG + "=?", new String[]{tag}, null, null, null);
        while (cursor.moveToNext()) {
            list.add(cursorToNote(cursor));
        }
        cursor.close();
        return list;
    }

    public void updateNote(QuestionNote note) {
        ContentValues values = new ContentValues();
        values.put(DbHelper.QUESTION, note.getQuestion());
        values.put(DbHelper.ANSWER, note.getAnswer());
        values.put(DbHelper.TAG, note.getTag());
        values.put(DbHelper.USER_NOTE, note.getUserNote());
        values.put(DbHelper.CATEGORY, note.getCategory());
        values.put(DbHelper.PHOTO_PATHS, note.getPhotoPaths());
        db.update(DbHelper.TABLE_NAME, values, DbHelper.ID + "=?", new String[]{String.valueOf(note.getId())});
    }

    public void deleteById(int id) {
        db.delete(DbHelper.TABLE_NAME, DbHelper.ID + "=?", new String[]{String.valueOf(id)});
    }

    public QuestionNote queryById(int id) {
        Cursor cursor = db.query(DbHelper.TABLE_NAME, null, DbHelper.ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor.moveToFirst()) {
            QuestionNote note = cursorToNote(cursor);
            cursor.close();
            return note;
        }
        cursor.close();
        return null;
    }

    @SuppressLint("Range")
    private QuestionNote cursorToNote(Cursor cursor) {
        return new QuestionNote(
                cursor.getInt(cursor.getColumnIndex(DbHelper.ID)),
                cursor.getString(cursor.getColumnIndex(DbHelper.QUESTION)),
                cursor.getString(cursor.getColumnIndex(DbHelper.ANSWER)),
                cursor.getString(cursor.getColumnIndex(DbHelper.TAG)),
                cursor.getString(cursor.getColumnIndex(DbHelper.USER_NOTE)),
                cursor.getString(cursor.getColumnIndex(DbHelper.CATEGORY)),
                cursor.getInt(cursor.getColumnIndex(DbHelper.KNOWLEDGE_ID)),
                cursor.getString(cursor.getColumnIndex(DbHelper.PHOTO_PATHS))
        );
    }

    public void close() {
        dbHelper.close();
    }
}