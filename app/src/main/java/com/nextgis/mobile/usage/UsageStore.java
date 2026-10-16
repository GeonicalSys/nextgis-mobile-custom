package com.nextgis.mobile.usage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** The installation identity and immutable outbox share one durable, backup-excluded database. */
public final class UsageStore extends SQLiteOpenHelper {
    static final int MAX_EVENTS = 100000;

    public UsageStore(Context context) {
        this(context, new File(context.getNoBackupFilesDir(), "mobile-usage.sqlite3"));
    }

    UsageStore(Context context, File file) {
        super(context, file.getAbsolutePath(), null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.execSQL("PRAGMA synchronous=FULL");
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE state(name TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE events(seq INTEGER PRIMARY KEY,id TEXT NOT NULL UNIQUE,payload TEXT NOT NULL)");
        put(db, "installation_id", UUID.randomUUID().toString());
        put(db, "next_seq", "0");
        put(db, "dropped", "0");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported usage database upgrade");
    }

    private static void put(SQLiteDatabase db, String name, String value) {
        ContentValues values = new ContentValues();
        values.put("name", name); values.put("value", value);
        if (db.insertWithOnConflict("state", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1)
            throw new android.database.sqlite.SQLiteException("Usage state could not be committed");
    }

    private static String get(SQLiteDatabase db, String name, String fallback) {
        try (Cursor cursor = db.rawQuery("SELECT value FROM state WHERE name=?", new String[]{name})) {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        }
    }

    public synchronized String installationId() { return get(getReadableDatabase(), "installation_id", ""); }
    public synchronized long dropped() { return Long.parseLong(get(getReadableDatabase(), "dropped", "0")); }

    public synchronized boolean append(JSONObject source, String inventory) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (inventory != null && inventory.equals(get(db, "inventory", ""))) return false;
            try (Cursor count = db.rawQuery("SELECT count(*) FROM events", null)) {
                count.moveToFirst();
                if (count.getLong(0) >= MAX_EVENTS) {
                    put(db, "dropped", Long.toString(Long.parseLong(get(db, "dropped", "0")) + 1));
                    db.setTransactionSuccessful();
                    return false;
                }
            }
            long seq = Long.parseLong(get(db, "next_seq", "0")) + 1;
            JSONObject event = new JSONObject(source.toString());
            event.put("id", UUID.randomUUID().toString()); event.put("seq", seq);
            ContentValues values = new ContentValues();
            values.put("seq", seq); values.put("id", event.getString("id")); values.put("payload", event.toString());
            db.insertOrThrow("events", null, values);
            put(db, "next_seq", Long.toString(seq));
            if (inventory != null) put(db, "inventory", inventory);
            db.setTransactionSuccessful();
            return true;
        } finally { db.endTransaction(); }
    }

    public synchronized JSONArray batch() throws JSONException {
        JSONArray events = new JSONArray();
        int size = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT payload FROM events ORDER BY seq LIMIT 50", null)) {
            while (cursor.moveToNext()) {
                String payload = cursor.getString(0);
                int bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (events.length() > 0 && size + bytes > 800000) break;
                events.put(new JSONObject(payload)); size += bytes;
            }
        }
        return events;
    }

    public synchronized void acknowledge(JSONArray requested, JSONArray accepted) throws JSONException {
        Set<String> permitted = new HashSet<>();
        for (int i = 0; i < requested.length(); i++) permitted.add(requested.getJSONObject(i).getString("id"));
        Set<String> confirmed = new HashSet<>();
        for (int i = 0; i < accepted.length(); i++) {
            String id = accepted.getString(i);
            if (!permitted.contains(id)) throw new JSONException("Unexpected acknowledgment");
            confirmed.add(id);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : confirmed) db.delete("events", "id=?", new String[]{id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
