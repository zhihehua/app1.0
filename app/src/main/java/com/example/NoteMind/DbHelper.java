package com.example.NoteMind;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notemind.db";
    private static final int DATABASE_VERSION = 3; // 提升版本号到 3 以解决降级冲突

    public static final String TABLE_NAME = "question_note";
    public static final String ID = "id";
    public static final String QUESTION = "question";
    public static final String ANSWER = "answer";
    public static final String TAG = "tag";
    public static final String USER_NOTE = "user_note";
    public static final String CATEGORY = "category";
    public static final String KNOWLEDGE_ID = "knowledge_id";
    public static final String PHOTO_PATHS = "photo_paths";
    public static final String CREATE_TIME = "create_time";

    public DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_NAME + " ("
                + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + QUESTION + " TEXT, "
                + ANSWER + " TEXT, "
                + TAG + " TEXT, "
                + USER_NOTE + " TEXT, "
                + CATEGORY + " TEXT, "
                + KNOWLEDGE_ID + " INTEGER DEFAULT 0, "
                + PHOTO_PATHS + " TEXT, "
                + CREATE_TIME + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(createTable);
        db.execSQL("CREATE INDEX idx_tag ON " + TABLE_NAME + "(" + TAG + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果是从版本 1 升级到版本 2 或 3
        if (oldVersion < 2) {
            upgradeToVersion2(db);
        }
        // 如果将来有版本 3 的特定结构变更，可以在这里继续添加 if (oldVersion < 3)
    }

    private void upgradeToVersion2(SQLiteDatabase db) {
        String createNewTable = "CREATE TABLE " + TABLE_NAME + "_new ("
                + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + QUESTION + " TEXT, "
                + ANSWER + " TEXT, "
                + TAG + " TEXT, "
                + USER_NOTE + " TEXT, "
                + CATEGORY + " TEXT, "
                + KNOWLEDGE_ID + " INTEGER DEFAULT 0, "
                + PHOTO_PATHS + " TEXT, "
                + CREATE_TIME + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(createNewTable);

        db.execSQL("INSERT INTO " + TABLE_NAME + "_new ("
                + QUESTION + ", " + ANSWER + ", " + TAG + ", " + USER_NOTE + ", " + CATEGORY
                + ") SELECT "
                + QUESTION + ", " + ANSWER + ", " + TAG + ", " + USER_NOTE + ", " + CATEGORY
                + " FROM " + TABLE_NAME);

        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        db.execSQL("ALTER TABLE " + TABLE_NAME + "_new RENAME TO " + TABLE_NAME);
        db.execSQL("CREATE INDEX idx_tag ON " + TABLE_NAME + "(" + TAG + ")");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 增加此方法可以防止降级时闪退，虽然通常不推荐直接降级，但在开发阶段可以避免崩溃
        // 这里可以选择什么都不做，或者删除表重建
    }
}