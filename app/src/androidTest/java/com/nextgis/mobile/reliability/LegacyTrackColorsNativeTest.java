package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.SharedTrackStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static com.nextgis.maplib.map.TrackLayer.*;
import static org.junit.Assert.*;

/** A private v1 store exercises the real SQLiteOpenHelper upgrade without touching user tracks. */
@RunWith(AndroidJUnit4.class)
public class LegacyTrackColorsNativeTest {
    @Test public void realUpgradeColorsOldHistoryOnceAndPreservesCustomColorPointsAndSendFlags() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        File owned = new File(app.getCacheDir(),"legacy-colors-"+UUID.randomUUID());
        File directory = new File(owned,"shared-tracks"); assertTrue(directory.mkdirs());
        File path = new File(directory,"tracks.db");
        Context scoped = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return owned; }
        };
        SQLiteDatabase upgraded = null;
        try {
            try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path,null)) {
                db.execSQL("CREATE TABLE tracks(_id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,"
                        + "start INTEGER NOT NULL,end INTEGER,color TEXT,visible INTEGER NOT NULL)");
                db.execSQL("CREATE TABLE trackpoints(_id INTEGER PRIMARY KEY AUTOINCREMENT,lon REAL NOT NULL,"
                        + "lat REAL NOT NULL,ele REAL,fix TEXT,sat INTEGER,speed REAL,accuracy REAL,bearing REAL,"
                        + "segment INTEGER NOT NULL DEFAULT 0,time INTEGER NOT NULL,sent INTEGER NOT NULL,"
                        + "session INTEGER NOT NULL,FOREIGN KEY(session) REFERENCES tracks(_id))");
                db.setVersion(1);
                for (int id=1;id<=24;id++) db.execSQL(
                        "INSERT INTO tracks(_id,name,start,end,color,visible) VALUES(?, ?,100,900,NULL,?)",
                        new Object[]{id,"Old "+id,id%2});
                db.execSQL("INSERT INTO tracks(_id,name,start,end,color,visible) VALUES(25,'Custom',200,NULL,?,1)",new Object[]{0xff5f789c});
                db.execSQL("INSERT INTO trackpoints(session,lon,lat,time,sent,segment) VALUES(1,1,2,300,0,3)");
                db.execSQL("INSERT INTO trackpoints(session,lon,lat,time,sent,segment) VALUES(25,3,4,400,1,4)");
            }
            SharedTrackStore store = SharedTrackStore.get(scoped); upgraded=store.getDatabase(false);
            assertEquals(2,upgraded.getVersion()); Set<Integer> colors=new HashSet<>();
            try (Cursor rows=upgraded.rawQuery("SELECT _id,name,start,end,color,visible FROM tracks ORDER BY _id",null)) {
                for(int id=1;id<=24;id++) {
                    assertTrue(rows.moveToNext()); assertEquals(id,rows.getLong(0));
                    assertEquals("Old "+id,rows.getString(1)); assertEquals(100,rows.getLong(2));
                    assertEquals(900,rows.getLong(3)); assertFalse(rows.isNull(4)); assertEquals(id%2,rows.getInt(5));
                    if(id<=12) colors.add(rows.getInt(4));
                }
                assertTrue(rows.moveToNext());assertEquals(0xff5f789c,rows.getInt(4));assertTrue(rows.isNull(3));
            }
            assertEquals(12,colors.size());
            assertEquals(1,DatabaseUtils.longForQuery(upgraded,"SELECT count(*) FROM trackpoints WHERE session=1 AND lon=1 AND lat=2 AND time=300 AND sent=0 AND segment=3",null));
            assertEquals(1,DatabaseUtils.longForQuery(upgraded,"SELECT count(*) FROM trackpoints WHERE session=25 AND lon=3 AND lat=4 AND time=400 AND sent=1 AND segment=4",null));
            upgraded.execSQL("UPDATE tracks SET color=123 WHERE _id=1");
            upgraded.close(); upgraded=store.getDatabase(false);
            assertEquals(123,DatabaseUtils.longForQuery(upgraded,"SELECT color FROM tracks WHERE _id=1",null));
        } finally {
            if(upgraded!=null && upgraded.isOpen())upgraded.close();
            // Only this test's UUID-scoped private database and its SQLite sidecars are owned here.
            assertTrue(SQLiteDatabase.deleteDatabase(path));
            assertTrue(directory.delete()); assertTrue(owned.delete());
        }
    }
}
