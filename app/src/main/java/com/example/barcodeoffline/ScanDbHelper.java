package com.example.barcodeoffline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite历史记录管理
 * 替代SharedPreferences，支持搜索、筛选、统计
 */
public class ScanDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "barcode_history.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "scan_history";

    public static final int MODE_SCAN = 0;
    public static final int MODE_GENERATE = 1;

    public static class Record {
        public long id;
        public int mode;        // 0=scan, 1=generate
        public String format;   // QR_CODE, CODE_128, EAN_13, ...
        public String content;
        public long timestamp;
        public int batchIndex;  // 批量扫描序号, 0=非批量

        public Record() {}

        public Record(int mode, String format, String content, int batchIndex) {
            this.mode = mode;
            this.format = format;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.batchIndex = batchIndex;
        }

        public String getDisplayText() {
            String modeStr = mode == MODE_SCAN ? "扫描" : "生成";
            String batchStr = batchIndex > 0 ? " #" + batchIndex : "";
            String time = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                    .format(new java.util.Date(timestamp));
            return time + "  [" + modeStr + batchStr + "/" + format + "]  " + content;
        }
    }

    public ScanDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "mode INTEGER NOT NULL DEFAULT 0," +
                "format TEXT," +
                "content TEXT NOT NULL," +
                "timestamp INTEGER NOT NULL," +
                "batch_index INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX idx_timestamp ON " + TABLE + "(timestamp DESC)");
        db.execSQL("CREATE INDEX idx_content ON " + TABLE + "(content)");
        db.execSQL("CREATE INDEX idx_mode ON " + TABLE + "(mode)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    /** 插入记录，返回ID */
    public long insert(Record record) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("mode", record.mode);
        cv.put("format", record.format);
        cv.put("content", record.content);
        cv.put("timestamp", record.timestamp);
        cv.put("batch_index", record.batchIndex);
        long id = db.insert(TABLE, null, cv);
        record.id = id;
        return id;
    }

    /** 查询最近N条记录 */
    public List<Record> queryRecent(int limit) {
        return query("SELECT * FROM " + TABLE + " ORDER BY timestamp DESC LIMIT " + limit);
    }

    /** 按关键词搜索 */
    public List<Record> search(String keyword) {
        return query("SELECT * FROM " + TABLE + " WHERE content LIKE '%" +
                keyword.replace("'", "''") + "%' ORDER BY timestamp DESC LIMIT 200");
    }

    /** 按模式筛选 */
    public List<Record> queryByMode(int mode) {
        return query("SELECT * FROM " + TABLE + " WHERE mode=" + mode + " ORDER BY timestamp DESC LIMIT 200");
    }

    /** 按格式筛选 */
    public List<Record> queryByFormat(String format) {
        return query("SELECT * FROM " + TABLE + " WHERE format='" +
                format.replace("'", "''") + "' ORDER BY timestamp DESC LIMIT 200");
    }

    /** 删除单条记录 */
    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    /** 清空所有记录 */
    public void deleteAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /** 统计总数 */
    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null);
        c.moveToFirst();
        int n = c.getInt(0);
        c.close();
        return n;
    }

    /** 统计今日扫描数 */
    public int countToday() {
        long todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 100L));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE mode=0 AND timestamp>=?",
                new String[]{String.valueOf(todayStart)});
        c.moveToFirst();
        int n = c.getInt(0);
        c.close();
        return n;
    }

    /** 统计不重复内容数 */
    public int countDistinct() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(DISTINCT content) FROM " + TABLE + " WHERE mode=0", null);
        c.moveToFirst();
        int n = c.getInt(0);
        c.close();
        return n;
    }

    /** 获取去重的内容列表（用于批量导出） */
    public List<String> getDistinctContents() {
        List<String> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT content FROM " + TABLE + " ORDER BY MAX(timestamp) DESC", null);
        while (c.moveToNext()) {
            list.add(c.getString(0));
        }
        c.close();
        return list;
    }

    /** 导出全部记录为CSV格式 */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("ID,类型,格式,内容,时间,批量序号\n");
        List<Record> records = query("SELECT * FROM " + TABLE + " ORDER BY timestamp DESC");
        for (Record r : records) {
            sb.append(r.id).append(",");
            sb.append(r.mode == MODE_SCAN ? "扫描" : "生成").append(",");
            sb.append(escapeCsv(r.format)).append(",");
            sb.append(escapeCsv(r.content)).append(",");
            sb.append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date(r.timestamp))).append(",");
            sb.append(r.batchIndex).append("\n");
        }
        return sb.toString();
    }

    /** 导出全部记录为JSON格式 */
    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        List<Record> records = query("SELECT * FROM " + TABLE + " ORDER BY timestamp DESC");
        for (int i = 0; i < records.size(); i++) {
            Record r = records.get(i);
            sb.append("  {");
            sb.append("\"id\":").append(r.id).append(",");
            sb.append("\"type\":\"").append(r.mode == MODE_SCAN ? "scan" : "generate").append("\",");
            sb.append("\"format\":\"").append(escapeJson(r.format)).append("\",");
            sb.append("\"content\":\"").append(escapeJson(r.content)).append("\",");
            sb.append("\"timestamp\":").append(r.timestamp).append(",");
            sb.append("\"batchIndex\":").append(r.batchIndex);
            sb.append("}");
            if (i < records.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    // ========== 内部方法 ==========

    private List<Record> query(String sql) {
        List<Record> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        while (c.moveToNext()) {
            Record r = new Record();
            r.id = c.getLong(c.getColumnIndexOrThrow("id"));
            r.mode = c.getInt(c.getColumnIndexOrThrow("mode"));
            r.format = c.getString(c.getColumnIndexOrThrow("format"));
            r.content = c.getString(c.getColumnIndexOrThrow("content"));
            r.timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"));
            r.batchIndex = c.getInt(c.getColumnIndexOrThrow("batch_index"));
            list.add(r);
        }
        c.close();
        return list;
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
