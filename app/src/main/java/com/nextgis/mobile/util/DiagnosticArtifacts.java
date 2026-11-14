package com.nextgis.mobile.util;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.UnderlayFiles;
import com.nextgis.mobile.BuildConfig;
import io.sentry.ISerializer;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryItemType;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Immutable archives of the already approved SDK envelope; never scans project files or logs. */
final class DiagnosticArtifacts {
    private static final long MAX_ZIP=16L*1024*1024,MAX_RAW=40L*1024*1024,MAX_QUEUE=64L*1024*1024;
    private static final Object PROCESS_LOCK=new Object();
    private static volatile DiagnosticArtifacts current;
    final File directory;
    private final Context context;
    private final String endpoint,token;

    static DiagnosticArtifacts current() {return current;}
    static synchronized void initializeProduction(Context context) {
        if(!ProductionTelemetry.enabled()||current!=null)return;
        try {current=new DiagnosticArtifacts(context,ProductionTelemetry.artifactEndpoint(),ProductionTelemetry.artifactToken(),
                new File(context.getNoBackupFilesDir(),"diagnostic-artifacts-v1"),false);}
        catch(Exception unavailable) {Log.w("AppDiagnostics","Diagnostic archive queue is unavailable; SDK reports remain queued");}
    }
    static DiagnosticArtifacts forTests(Context context,String endpoint,String token,File directory) throws Exception {
        if(!BuildConfig.DEBUG||!BuildConfig.FIELD_VERIFICATION
                ||!"com.nextgis.mobile.verification.debug".equals(context.getPackageName()))
            throw new IllegalStateException("Isolated verification Debug only");
        DiagnosticArtifacts value=new DiagnosticArtifacts(context,endpoint,token,directory,true);current=value;return value;
    }
    static synchronized void endTests(DiagnosticArtifacts expected) {
        if(!BuildConfig.DEBUG||current!=expected)throw new IllegalStateException("Unknown diagnostic fixture");current=null;
    }
    private DiagnosticArtifacts(Context context,String endpoint,String token,File directory,boolean loopback) throws Exception {
        URI uri=new URI(endpoint);
        if(uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||token==null
                ||!token.matches("[A-Za-z0-9._~+/=\\-]{16,4096}"))throw new IOException("Invalid diagnostic archive configuration");
        if(loopback) {
            if(!"http".equals(uri.getScheme())||!"127.0.0.1".equals(uri.getHost()))throw new IOException("Loopback fixture required");
        } else if(!"https://apps-geonical.ru/error-artifacts".equals(endpoint))throw new IOException("Unexpected artifact destination");
        UnderlayFiles.requireChild(context.getNoBackupFilesDir(),directory);
        if(Files.isSymbolicLink(directory.toPath()))throw new IOException("Archive queue is a link");
        UnderlayFiles.directory(directory);this.directory=directory.getCanonicalFile();this.context=context.getApplicationContext();
        this.endpoint=endpoint;this.token=token;
        locked(()->{try(SQLiteDatabase db=acknowledgements()) { }return null;});
    }
    String url(String id) {return endpoint+"/events/"+id;}
    private File file(String name) throws IOException {
        File file=new File(directory,name);UnderlayFiles.requireChild(directory,file);
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Archive queue entry is a link");return file;
    }
    private interface Locked<T> {T run() throws Exception;}
    private <T> T locked(Locked<T> action) throws Exception {
        synchronized(PROCESS_LOCK) {
            try(RandomAccessFile lock=new RandomAccessFile(file(".queue.lock"),"rw");FileLock ignored=lock.getChannel().lock()) {
                return action.run();
            }
        }
    }
    private List<File> queued() throws IOException {
        File[] files=directory.listFiles();if(files==null||files.length>2000)throw new IOException("Cannot enumerate diagnostic queue");
        List<File> result=new ArrayList<>();
        for(File candidate:files)if(candidate.getName().matches("[a-f0-9]{32}\\.zip")) {
            File checked=file(candidate.getName());if(!checked.isFile())throw new IOException("Invalid diagnostic archive");result.add(checked);
        }
        result.sort(Comparator.comparing(File::getName));return result;
    }
    boolean pending() throws Exception {return locked(()->!queued().isEmpty());}
    private SQLiteDatabase acknowledgements() throws IOException {
        SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file("acknowledgements.db"),null);
        try {
            if(db.getVersion()!=0&&db.getVersion()!=1)throw new IOException("Unknown archive acknowledgement schema");
            db.execSQL("PRAGMA synchronous=FULL");db.setMaximumSize(8L*1024*1024);
            db.execSQL("CREATE TABLE IF NOT EXISTS acknowledgements(event_id TEXT PRIMARY KEY,sha256 TEXT NOT NULL)");
            db.setVersion(1);return db;
        }catch(Exception error) {db.close();throw error;}
    }
    private String acknowledged(String id) throws Exception {
        try(SQLiteDatabase db=acknowledgements();Cursor c=db.rawQuery("SELECT sha256 FROM acknowledgements WHERE event_id=?",new String[]{id})) {
            if(!c.moveToFirst())return null;String hash=c.getString(0);
            if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid archive acknowledgement");return hash;
        }
    }
    private void recordAcknowledgement(String id,String hash) throws Exception {
        try(SQLiteDatabase db=acknowledgements()) {
            db.beginTransaction();try {
                ContentValues row=new ContentValues();row.put("event_id",id);row.put("sha256",hash);
                db.insertWithOnConflict("acknowledgements",null,row,SQLiteDatabase.CONFLICT_IGNORE);
                try(Cursor c=db.rawQuery("SELECT sha256 FROM acknowledgements WHERE event_id=?",new String[]{id})) {
                    if(!c.moveToFirst()||!hash.equals(c.getString(0)))throw new IOException("Archive acknowledgement differs");
                }
                db.setTransactionSuccessful();
            }finally {db.endTransaction();}
        }
    }
    static String sha256(File file) throws IOException {
        MessageDigest digest=UnderlayFiles.digest();byte[] bytes=new byte[65536];
        try(InputStream input=new FileInputStream(file)) {int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);}
        return UnderlayFiles.hex(digest.digest());
    }
    private void verify(File archive,String id) throws Exception {
        if(archive.length()<1||archive.length()>MAX_ZIP)throw new IOException("Invalid diagnostic archive size");
        try(ZipFile zip=new ZipFile(archive)) {
            if(zip.size()!=2)throw new IOException("Unknown diagnostic archive entries");
            ZipEntry manifest=zip.getEntry("manifest.json"),report=zip.getEntry("report.envelope");
            if(manifest==null||report==null||report.getSize()<1||report.getSize()>MAX_RAW)throw new IOException("Invalid diagnostic archive");
            JSONObject value;
            try(InputStream input=zip.getInputStream(manifest)) {
                value=new JSONObject(new String(UnderlayFiles.readBounded(input,4096),StandardCharsets.UTF_8));
            }
            if(value.getInt("schema")!=1||!"mobile-approved-envelope".equals(value.getString("kind"))
                    ||!id.equals(value.getString("event_id")))throw new IOException("Diagnostic archive identity differs");
        }
    }
    /** The first archive for an event UUID wins, including cache replay after an app update. */
    void store(ISerializer serializer,SentryEnvelope envelope) throws Exception {
        if(envelope.getHeader().getEventId()==null)return;
        boolean report=false;
        for(SentryEnvelopeItem item:envelope.getItems())if(item.getHeader().getType()==SentryItemType.Event)report=true;
        if(!report)return;
        String id=envelope.getHeader().getEventId().toString();if(!id.matches("[a-f0-9]{32}"))throw new IOException("Invalid event identity");
        locked(()->{
            if(acknowledged(id)!=null)return null;
            File destination=file(id+".zip");if(destination.isFile()) {verify(destination,id);return null;}
            long used=0;File[] all=directory.listFiles();if(all==null||all.length>256)throw new IOException("Diagnostic queue is full");
            for(File present:all) {file(present.getName());used=Math.addExact(used,present.length());}
            if(used>MAX_QUEUE-MAX_ZIP||directory.getUsableSpace()<MAX_ZIP+8L*1024*1024)throw new IOException("Diagnostic queue budget exhausted");
            File temporary=file("."+id+"-"+UUID.randomUUID()+".pending");
            try {
                try(FileOutputStream raw=new FileOutputStream(temporary);ZipOutputStream zip=new ZipOutputStream(raw)) {
                    ZipEntry manifest=new ZipEntry("manifest.json");manifest.setTime(0);zip.putNextEntry(manifest);
                    zip.write(new JSONObject().put("schema",1).put("kind","mobile-approved-envelope").put("event_id",id)
                            .toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                    ZipEntry reportEntry=new ZipEntry("report.envelope");reportEntry.setTime(0);zip.putNextEntry(reportEntry);
                    OutputStream bounded=new OutputStream() {
                        long count;
                        @Override public void write(int value) throws IOException {count++;check();zip.write(value);}
                        @Override public void write(byte[] bytes,int offset,int length) throws IOException {count+=length;check();zip.write(bytes,offset,length);}
                        private void check() throws IOException {
                            if(count>MAX_RAW||temporary.length()>MAX_ZIP||Thread.currentThread().isInterrupted())
                                throw new IOException("Diagnostic archive exceeds its budget or was cancelled");
                        }
                        @Override public void close() { }
                    };
                    serializer.serialize(envelope,bounded);zip.closeEntry();zip.finish();raw.getFD().sync();
                }
                verify(temporary,id);
                Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
            } finally {Files.deleteIfExists(temporary.toPath());}
            return null;
        });
        AppDiagnostics.scheduleRetry(context);
    }
    /** An ACK must match both immutable event UUID and the exact bytes that were sent. */
    boolean retry() throws Exception {
        if(!new NetworkUtil(context).isNetworkAvailable())return false;
        List<File> archives=locked(this::queued);int sent=0;
        for(File archive:archives) {
            if(sent++>=8||Thread.currentThread().isInterrupted())break;
            String id=archive.getName().substring(0,32);
            String hash=locked(()->{verify(archive,id);return sha256(archive);});
            String known=locked(()->acknowledged(id));
            if(known!=null) {
                locked(()->{if(!known.equals(sha256(archive)))throw new IOException("Acknowledged archive differs");
                    if(!archive.delete())throw new IOException("Archive cleanup deferred");return null;});continue;
            }
            HttpURLConnection connection=(HttpURLConnection)new URL(endpoint+"/api/"+id).openConnection();
            try {
                connection.setRequestMethod("PUT");connection.setDoOutput(true);connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);
                connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/zip");
                connection.setRequestProperty("X-Content-SHA256",hash);connection.setFixedLengthStreamingMode(archive.length());
                try(InputStream input=new FileInputStream(archive);OutputStream output=connection.getOutputStream()) {
                    byte[] bytes=new byte[65536];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
                }
                int status=connection.getResponseCode();if(status!=200&&status!=201)continue;
                JSONObject ack;
                try(InputStream input=connection.getInputStream()) {
                    ack=new JSONObject(new String(UnderlayFiles.readBounded(input,4096),StandardCharsets.UTF_8));
                }
                if(!id.equals(ack.optString("event_id"))||!hash.equals(ack.optString("sha256")))continue;
                locked(()->{verify(archive,id);if(!hash.equals(sha256(archive)))throw new IOException("Archive changed before ACK");
                    recordAcknowledgement(id,hash);
                    if(!archive.delete())throw new IOException("Archive ACK cleanup deferred");return null;});
            }catch(IOException deferred) {Log.i("AppDiagnostics","Diagnostic archive delivery deferred");}
            finally {connection.disconnect();}
        }
        return !pending();
    }
}
