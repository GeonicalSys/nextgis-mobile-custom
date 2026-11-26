package com.nextgis.mobile.reliability;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import com.nextgis.maplib.datasource.*;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.*;
import com.nextgis.maplib.util.*;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.LocalChangesWorker;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real saved line, SQLite outbox, disabled account schedule and loopback NGW HTTP only. */
@RunWith(AndroidJUnit4.class)
public class LocalChangesUploadTest {
    private EmulatorNetworkFixture network;
    @Before public void connectedFixture() throws Exception {
        network = new EmulatorNetworkFixture(InstrumentationRegistry.getInstrumentation().getTargetContext()); network.connected(true);
    }
    @After public void restoreConnectivity() throws Exception { if (network != null) network.close(); }
    static class Endpoint implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        final List<String> mutations = new CopyOnWriteArrayList<>();
        volatile int status = 200;
        volatile Throwable error;
        volatile boolean holdRead = false;
        volatile boolean dropCreationResponse = false, serveFeatures = false;
        volatile boolean txMode, dropTransactionCommitResponse, transactionNotCommitted;
        final java.util.concurrent.atomic.AtomicInteger unavailableTransactionResults = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger transactionAllocations = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger transactionStages = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger transactionCommits = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger transactionResultReads = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger featureWrites = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger plainFeaturePosts = new java.util.concurrent.atomic.AtomicInteger();
        final List<String> transactionRequests = new CopyOnWriteArrayList<>();
        final java.util.Map<Integer,JSONObject> stagedTransactions = new ConcurrentHashMap<>();
        volatile JSONObject stagedOperation;
        volatile String creationReply;
        volatile String resourceMetadata;
        final java.util.concurrent.atomic.AtomicInteger resourceReads = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger versionReads = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.Map<Integer,String> remoteFeatures = new ConcurrentHashMap<>();
        final CountDownLatch readEntered = new CountDownLatch(1), releaseRead = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger remoteId = new java.util.concurrent.atomic.AtomicInteger(42);
        final Thread thread;
        private static String headerLine(InputStream input) throws IOException {
            ByteArrayOutputStream line=new ByteArrayOutputStream();int next;
            while((next=input.read())!=-1) {
                if(next=='\n')return line.toString(StandardCharsets.US_ASCII.name()).replaceFirst("\r$","");
                if(line.size()>=8192)throw new IOException("Oversized fixture header");line.write(next);
            }
            return line.size()==0?null:line.toString(StandardCharsets.US_ASCII.name());
        }
        Endpoint() throws Exception {
            thread = new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    InputStream reader = socket.getInputStream();
                    String request = headerLine(reader), line; int length = 0;
                    while ((line = headerLine(reader)) != null && !line.isEmpty())
                        if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    if(length<0||length>8*1024*1024)throw new IOException("Oversized fixture request");
                    byte[] body = new byte[length]; int read = 0, count;
                    while (read < length && (count = reader.read(body, read, length - read)) > 0) read += count;
                    if(read!=length)throw new EOFException("Incomplete fixture request");
                    boolean mutation = !request.startsWith("GET ");
                    if (mutation) mutations.add(request + "\n" + new String(body, 0, read,StandardCharsets.UTF_8));
                    if (!mutation && holdRead) { readEntered.countDown(); assertTrue(releaseRead.await(30, TimeUnit.SECONDS)); }
                    int code = mutation ? status : 404;
                    String response = "{}";
                    if(!mutation&&request.startsWith("GET /api/component/pyramid/pkg_version ")) {
                        versionReads.incrementAndGet();code=200;
                        response=new JSONObject().put("nextgisweb",txMode?"5.7.0":"4.0.0").toString();
                    } else if (!mutation && resourceMetadata != null && request.startsWith("GET /api/resource/1329 ")) {
                        resourceReads.incrementAndGet(); code = 200; response = resourceMetadata;
                    } else if(txMode&&URI.create(request.split(" ",3)[1]).getPath()
                            .startsWith("/api/resource/1329/feature/transaction/")) {
                        transactionRequests.add(request);
                        String[] parts=request.split(" ",3);String method=parts[0],path=URI.create(parts[1]).getPath();
                        String collection="/api/resource/1329/feature/transaction/";
                        if(method.equals("POST")&&path.equals(collection)) {
                            int tid=500+transactionAllocations.incrementAndGet();code=200;
                            response=new JSONObject().put("id",tid).toString();
                        } else {
                            int tid=Integer.parseInt(path.substring(collection.length()));
                            if(method.equals("PUT")) {
                                transactionStages.incrementAndGet();JSONArray operations=new JSONArray(new String(body,0,read,StandardCharsets.UTF_8));
                                assertEquals(1,operations.length());JSONArray pair=operations.getJSONArray(0);
                                assertEquals(2,pair.length());assertEquals(0,pair.getInt(0));
                                stagedOperation=new JSONObject(pair.getJSONObject(1).toString());
                                assertEquals("feature.create",stagedOperation.getString("action"));
                                stagedTransactions.put(tid,stagedOperation);code=200;
                            } else if(method.equals("POST")) {
                                transactionCommits.incrementAndGet();JSONObject operation=stagedTransactions.get(tid);
                                assertNotNull("Only a staged operation can commit",operation);
                                // A repeated commit would really duplicate the external PostGIS write in this fixture.
                                featureWrites.incrementAndGet();int fid=remoteId.getAndIncrement();
                                byte[] wkb=android.util.Base64.decode(operation.getString("geom"),android.util.Base64.DEFAULT);
                                double[] xy=transactionLineCoordinates(wkb);
                                String wkt="LINESTRING ("+xy[0]+" "+xy[1]+", "+xy[2]+" "+xy[3]+")";
                                remoteFeatures.put(fid,new JSONObject().put("id",fid).put("geom",wkt)
                                        .put("fields",new JSONObject(operation.getJSONObject("fields").toString())).toString());
                                if(dropTransactionCommitResponse) {dropTransactionCommitResponse=false;continue;}
                                code=200;response="{\"status\":\"committed\"}";
                            } else if(method.equals("GET")) {
                                transactionResultReads.incrementAndGet();
                                if(unavailableTransactionResults.get()>0) {
                                    unavailableTransactionResults.decrementAndGet();code=503;response="{}";
                                } else if(transactionNotCommitted) {
                                    code=409;response="{\"exception\":\"TransactionNotCommitted\"}";
                                } else {
                                    code=200;response=new JSONArray().put(new JSONArray().put(0)
                                            .put(new JSONObject().put("action","feature.create").put("fid",42))).toString();
                                }
                            }
                        }
                    } else if(request.startsWith("PUT /api/resource/1329/feature/")&&code==200) {
                        java.util.regex.Matcher item=java.util.regex.Pattern.compile("/api/resource/1329/feature/(\\d+)")
                                .matcher(URI.create(request.split(" ",3)[1]).getPath());
                        if(item.matches()) {
                            int fid=Integer.parseInt(item.group(1));String existing=remoteFeatures.get(fid);
                            if(existing!=null) {
                                JSONObject updated=new JSONObject(existing),patch=new JSONObject(new String(body,0,read,StandardCharsets.UTF_8));
                                if(patch.has("fields")) {
                                    JSONObject values=updated.optJSONObject("fields");if(values==null)values=new JSONObject();
                                    JSONObject changed=patch.getJSONObject("fields");
                                    for(Iterator<String> names=changed.keys();names.hasNext();) {
                                        String name=names.next();values.put(name,changed.get(name));
                                    }
                                    updated.put("fields",values);
                                }
                                if(patch.has("geom"))updated.put("geom",patch.get("geom"));
                                remoteFeatures.put(fid,updated.toString());
                            }
                        }
                    } else if (request.startsWith("POST ") && code == 200) {
                        plainFeaturePosts.incrementAndGet();featureWrites.incrementAndGet();
                        int id = remoteId.getAndIncrement();
                        remoteFeatures.put(id, new JSONObject(new String(body,0,read,StandardCharsets.UTF_8)).put("id",id).toString());
                        if (dropCreationResponse) continue;
                        response = creationReply != null ? creationReply : "{\"id\":" + id + "}";
                    } else if (!mutation && serveFeatures) {
                        java.util.regex.Matcher match = java.util.regex.Pattern.compile("/feature/(\\d+)").matcher(request);
                        boolean item=match.find();
                        if (item && remoteFeatures.containsKey(Integer.parseInt(match.group(1)))) {
                            code = 200; response = remoteFeatures.get(Integer.parseInt(match.group(1)));
                        } else if(!item&&request.startsWith("GET /api/resource/1329/feature/")) {
                            JSONArray rows=new JSONArray();
                            for(String feature:remoteFeatures.values())rows.put(new JSONObject(feature));
                            code=200;response=rows.toString();
                        }
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 " + code + " Result\r\nContent-Type: application/json\r\nContent-Length: "
                            + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(bytes); socket.getOutputStream().flush();
                } catch (SocketException stopped) { if (!server.isClosed()) error = stopped; }
                catch (Throwable failed) { error = failed; }
            }, "isolated-local-ngw"); thread.start();
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
        @Override public void close() throws Exception { server.close(); thread.join(10000); assertNull(error); }
    }
    static class Fixture implements AutoCloseable {
        final GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
        final Account account = new Account("local-delivery-" + UUID.randomUUID(), app.getAccountsType());
        final String layerBasename;
        NGWVectorLayerUI layer;
        final String path = map.getPath().getCanonicalPath();
        final File registry = new File(app.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP), "collector_projects/collector_projects_registry.json");
        final byte[] registryBefore = registry.isFile() ? Files.readAllBytes(registry.toPath()) : null;
        final File deliveryOwner = new File(map.getPath(),ProjectRecoveryGuard.DELIVERY_OWNER);
        final byte[] deliveryOwnerBefore = deliveryOwner.isFile()?Files.readAllBytes(deliveryOwner.toPath()):null;
        final boolean lisaHistory;
        final boolean legacyHistory;
        Fixture(Endpoint endpoint) throws Exception {
            this(endpoint, false);
        }
        Fixture(Endpoint endpoint, boolean lisaHistory) throws Exception {
            this(endpoint, lisaHistory, false);
        }
        Fixture(Endpoint endpoint, boolean lisaHistory, boolean legacyHistory) throws Exception {
            this.lisaHistory = lisaHistory;
            this.legacyHistory = legacyHistory;
            this.layerBasename = legacyHistory
                    ? "local_delivery_line_" + UUID.randomUUID().toString().replace("-", "") : "local_delivery_line";
            assertTrue("Isolated emulator only", "ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE));
            cancelJobs();
            Bundle data = new Bundle(); data.putString("url", endpoint.url()); data.putString("login", "synthetic");
            assertTrue(AccountManager.get(app).addAccountExplicitly(account, "", data));
            ContentResolver.setIsSyncable(account, app.getAuthority(), 0);
            ContentResolver.setSyncAutomatically(account, app.getAuthority(), false);
            layer = create(map);
        }
        NGWVectorLayerUI create(MapContentProviderHelper owner) throws Exception {
            NGWVectorLayerUI vector = new NGWVectorLayerUI(app, new File(owner.getPath(), layerBasename));
            owner.addLayer(vector); vector.setName("Saved line"); vector.beginBulkImport();
            vector.create(GeoConstants.GTLineString, lisaHistory ? lisaHistoryFields(legacyHistory)
                    : Collections.singletonList(new Field(GeoConstants.FTString, "name", "Name")));
            vector.endBulkImport(); vector.setAccountName(account.name); vector.setRemoteId(1329);
            JSONObject config = vector.toJSON(); config.put("ngw_layer_type", Connection.NGWResourceTypePostgisLayer); vector.fromJSON(config);
            assertTrue(vector.save()); assertTrue(owner.save()); return vector;
        }
        long savedLine(NGWVectorLayer vector, boolean automatic) throws Exception {
            vector.setSyncType(Constants.SYNC_ALL);
            vector.setSyncDirection(automatic ? 3 : 2);
            ContentValues row = new ContentValues(); row.put("name", "field-line");
            row.put(Constants.FIELD_GEOM, GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)", GeoConstants.CRS_WEB_MERCATOR).toBlob());
            long id = vector.insertAddChanges(row); assertTrue(id >= 10000000);
            vector.setSyncDirection(3); assertTrue(vector.save()); return id;
        }
        long raw226Line(String name) throws Exception {
            assertTrue(lisaHistory && legacyHistory);
            layer.setSyncType(Constants.SYNC_ALL);layer.setSyncDirection(2);
            JSONObject config=layer.toJSON();config.put("ngw_version_major",4);layer.fromJSON(config);
            SQLiteDatabase db=DatabaseContext.getDatabaseForLayer(layer,false);
            String changes=layer.getChangeTableName();
            assertEquals(0,FeatureChanges.getChangeCount(db,changes));
            db.execSQL("DROP TABLE \""+changes+"\"");
            db.execSQL("CREATE TABLE \""+changes+"\"(_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    +"feature_id INTEGER,operation INTEGER,attach_id INTEGER,attach_operation INTEGER)");
            ContentValues row=new ContentValues();row.put(Constants.FIELD_ID,10000001);
            row.put("name",name);row.put("created_at",1700000000000L);row.put("created_by","old-client");
            row.put("business_integer",0);row.putNull("business_long_null");row.putNull("business_real_null");
            row.put(Constants.FIELD_GEOM,GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",
                    GeoConstants.CRS_WEB_MERCATOR).toBlob());
            assertEquals(10000001,db.insertOrThrow(layer.getPath().getName(),null,row));
            raw226Change(10000001,Constants.CHANGE_OPERATION_NEW,-1,0);
            assertFalse("The historic queue must really lack the UUID column",FeatureChanges.hasOperationIdentity(db,changes));
            assertTrue(UploadOperationJournal.entries(db,layer.getPath().getName()).isEmpty());
            assertTrue(layer.save());assertTrue(map.save());
            return 10000001;
        }
        long raw226Change(long feature,int operation,long attachment,int attachmentOperation) {
            ContentValues change=new ContentValues();change.put(Constants.FIELD_FEATURE_ID,feature);
            change.put(Constants.FIELD_OPERATION,operation);change.put(Constants.FIELD_ATTACH_ID,attachment);
            change.put(Constants.FIELD_ATTACH_OPERATION,attachmentOperation);
            return DatabaseContext.getDatabaseForLayer(layer,false).insertOrThrow(layer.getChangeTableName(),null,change);
        }
        void reopenLayerFromSaved226Config() throws Exception {
            reopenSavedLayer();
            assertFalse(FeatureChanges.hasOperationIdentity(DatabaseContext.getDatabaseForLayer(layer,false),layer.getChangeTableName()));
            assertEquals(1,pending(layer));layer.setSyncDirection(3);assertTrue(layer.save());assertTrue(map.save());
        }
        void reopenSavedLayer() throws Exception {
            File directory=layer.getPath();int index=map.removeLayer(layer);layer.setParent(null);
            NGWVectorLayerUI reopened=new NGWVectorLayerUI(app,directory);
            assertTrue("The saved old layer must reopen",reopened.load());
            map.insertLayer(index,reopened);layer=reopened;
        }
        void enableReviewedTransactions() throws Exception {
            enableReviewedTransactions(5);
        }
        void enableReviewedTransactions(int cachedServerMajor) throws Exception {
            JSONObject config=layer.toJSON();config.put("ngw_version_major",cachedServerMajor);
            config.put(GeoConstants.GEOJSON_CRS,GeoConstants.CRS_WEB_MERCATOR);layer.fromJSON(config);
            layer.setSyncDirection(3);assertTrue(layer.save());assertTrue(map.save());
        }
        UploadOperationJournal.Entry creationReceipt() {
            for(UploadOperationJournal.Entry entry:UploadOperationJournal.entries(
                    DatabaseContext.getDatabaseForLayer(layer,false),layer.getPath().getName()))
                if("create".equals(entry.operation))return entry;
            throw new AssertionError("The legacy creation receipt is missing");
        }
        String reviewLegacyCreation(long feature) {
            UploadOperationJournal.Entry receipt=creationReceipt();
            return layer.prepareChangeReview(receipt.changeId,receipt.id,feature,-1,"create");
        }
        void cancelJobs() throws Exception { WorkManager.getInstance(app).cancelAllWorkByTag("local-changes-upload").getResult().get(10, TimeUnit.SECONDS); }
        long pending(NGWVectorLayer vector) { return FeatureChanges.getChangeCount(DatabaseContext.getDatabaseForLayer(vector, true), vector.getChangeTableName()); }
        boolean send() { return LocalChangesWorker.uploadProject(app, path); }
        void awaitSent(NGWVectorLayer vector) throws Exception {
            long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
            while (pending(vector) > 0 && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            assertEquals("The committed edit must trigger automatic delivery", 0, pending(vector));
        }
        @Override public void close() throws Exception {
            Thread.sleep(1200); cancelJobs();
            assertTrue(layer.delete(false)); map.save();
            assertTrue(AccountManager.get(app).removeAccountExplicitly(account));
            if(deliveryOwnerBefore!=null)Files.write(deliveryOwner.toPath(),deliveryOwnerBefore);
            else if(deliveryOwner.exists())assertTrue(deliveryOwner.delete());
        }
    }
    private static final String[] LISA_HISTORY_FIELDS = {"created_at", "updated_at", "deleted_at",
            "created_by", "updated_by", "deleted_by", "deletion_operation_id"};
    private static List<Field> lisaHistoryFields() {
        return lisaHistoryFields(false);
    }
    private static List<Field> lisaHistoryFields(boolean legacy) {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field(GeoConstants.FTString, "name", "Name"));
        for (String name : legacy ? new String[]{"created_at", "updated_at"}
                : new String[]{"created_at", "updated_at", "deleted_at"})
            fields.add(new Field(GeoConstants.FTDateTime, name, name));
        for (String name : legacy ? new String[]{"created_by", "updated_by"}
                : new String[]{"created_by", "updated_by", "deleted_by"})
            fields.add(new Field(GeoConstants.FTString, name, name));
        if (!legacy) fields.add(new Field(GeoConstants.FTLong, "deletion_operation_id", "deletion_operation_id"));
        fields.add(new Field(GeoConstants.FTInteger, "business_integer", "business_integer"));
        fields.add(new Field(GeoConstants.FTLong, "business_long_null", "business_long_null"));
        fields.add(new Field(GeoConstants.FTReal, "business_real_null", "business_real_null"));
        return fields;
    }
    private static JSONObject lisaResourceMetadata() throws Exception {
        return lisaResourceMetadata(false);
    }
    static JSONObject lisaResourceMetadata(boolean legacy) throws Exception {
        JSONArray fields = new JSONArray();
        for (Field field : lisaHistoryFields(legacy)) {
            String datatype;
            switch (field.getType()) {
                case GeoConstants.FTDateTime: datatype = "DATETIME"; break;
                case GeoConstants.FTLong: datatype = "BIGINT"; break;
                case GeoConstants.FTInteger: datatype = "INTEGER"; break;
                case GeoConstants.FTReal: datatype = "REAL"; break;
                default: datatype = "STRING";
            }
            fields.put(new JSONObject().put("keyname", field.getName()).put("datatype", datatype)
                    .put("display_name", field.getAlias()).put("required", false));
        }
        return new JSONObject().put("resource", new JSONObject().put("id", 1329).put("cls", "postgis_layer"))
                .put("postgis_layer", new JSONObject().put("schema", "dynamic").put("table", "field_lines")
                        .put("geometry_type", "LINESTRING").put("srs", new JSONObject().put("id", 3857)))
                .put("feature_layer", new JSONObject().put("fields", fields));
    }
    private static void assertLocalHistoryAndNumbers(Fixture f, long featureId) {
        SQLiteDatabase db = DatabaseContext.getDatabaseForLayer(f.layer, false);
        try (Cursor row = db.query(f.layer.getPath().getName(), null, Constants.FIELD_ID + "=?",
                new String[]{Long.toString(featureId)}, null, null, null)) {
            assertTrue("The saved feature must remain in the layer", row.moveToFirst());
            assertEquals(1700000000000L, row.getLong(row.getColumnIndexOrThrow("created_at")));
            assertEquals("server-created", row.getString(row.getColumnIndexOrThrow("created_by")));
            for (String name : new String[]{"updated_at", "deleted_at", "updated_by", "deleted_by",
                    "deletion_operation_id", "business_long_null", "business_real_null"})
                assertTrue(name + " must remain SQL NULL", row.isNull(row.getColumnIndexOrThrow(name)));
            assertEquals(0, row.getInt(row.getColumnIndexOrThrow("business_integer")));
        }
    }
    private static String queuedOperationId(Fixture f) {
        assertEquals("Only the current saved edit must be pending", 1L, f.pending(f.layer));
        SQLiteDatabase db = DatabaseContext.getDatabaseForLayer(f.layer, false);
        String changes = f.layer.getChangeTableName();
        long changeId = FeatureChanges.getLastChangeRecordId(db, changes);
        assertTrue("The saved edit must have a queue record", changeId != Constants.NOT_FOUND);
        String operationId = FeatureChanges.operationId(db, changes, changeId);
        assertNotNull("The saved edit must already have an immutable identity", operationId);
        assertFalse(operationId.isEmpty());
        return operationId;
    }
    private static void assertHistoryFreeDurablePayload(Fixture f, String request, String operationId,
                                                       String operation, String expectedName) throws Exception {
        String payload = request.substring(request.indexOf('\n') + 1);
        JSONObject body = new JSONObject(payload), fields = body.getJSONObject("fields");
        assertEquals(expectedName, fields.getString("name"));
        for (String name : LISA_HISTORY_FIELDS)
            assertFalse(name + " is owned by the server", fields.has(name));
        assertEquals(4, fields.length());
        assertTrue(fields.has("business_integer")); assertEquals(0, fields.getInt("business_integer"));
        assertTrue(fields.has("business_long_null")); assertFalse(fields.isNull("business_long_null"));
        assertEquals(0L, fields.getLong("business_long_null"));
        assertTrue(fields.has("business_real_null")); assertFalse(fields.isNull("business_real_null"));
        assertEquals(0d, fields.getDouble("business_real_null"), 0d);
        assertTrue(body.getString("geom").contains("LINESTRING"));
        UploadOperationJournal.Entry receipt = UploadOperationJournal.find(
                DatabaseContext.getDatabaseForLayer(f.layer, false), f.layer.getPath().getName(), operationId);
        assertNotNull("The current saved edit must have its own durable receipt", receipt);
        assertEquals(operationId, receipt.id); assertEquals(operation, receipt.operation);
        assertEquals(UploadOperationJournal.CONFIRMED, receipt.state);
        assertEquals("The durable snapshot must be the exact HTTP body", receipt.payload, payload);
    }
    private static boolean flushBeforeReplacement(Fixture f) throws Exception {
        java.lang.reflect.Method flush = GISApplication.class.getDeclaredMethod(
                "flushLayerEditsBeforeReplacement", NGWVectorLayer.class);
        flush.setAccessible(true);
        return (Boolean) flush.invoke(f.app, f.layer);
    }
    private static void assertLegacyRow(Fixture f, long id, byte[] geometry, String name) {
        try (Cursor row = DatabaseContext.getDatabaseForLayer(f.layer, false).query(
                f.layer.getPath().getName(), null, Constants.FIELD_ID + "=?",
                new String[]{Long.toString(id)}, null, null, null)) {
            assertTrue(row.moveToFirst());
            assertEquals(name, row.getString(row.getColumnIndexOrThrow("name")));
            assertArrayEquals(geometry, row.getBlob(row.getColumnIndexOrThrow(Constants.FIELD_GEOM)));
            assertEquals(1700000000000L, row.getLong(row.getColumnIndexOrThrow("created_at")));
            assertEquals("old-client", row.getString(row.getColumnIndexOrThrow("created_by")));
            for (String field : new String[]{"updated_at", "updated_by", "business_long_null", "business_real_null"})
                assertTrue(field, row.isNull(row.getColumnIndexOrThrow(field)));
            for (String field : new String[]{"deleted_at", "deleted_by", "deletion_operation_id"})
                assertEquals("The old physical schema must survive until edits are delivered", -1, row.getColumnIndex(field));
        }
    }
    static String accepted226Line(long serverId,String name) throws Exception {
        return new JSONObject().put("id",serverId).put("geom","LINESTRING (1 2, 3 4)")
                .put("fields",new JSONObject().put("name",name).put("business_integer",0)
                        .put("business_long_null",0).put("business_real_null",0)).toString();
    }
    @Test public void real226QueueReopensUnknownAndOnlyAnExplicitRetryCanSendBeforeReplacement() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();
            long local=f.raw226Line("raw-226-line");f.reopenLayerFromSaved226Config();
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertFalse("Unknown old POST outcome cannot permit a layer replacement",flushBeforeReplacement(f));
            assertTrue(endpoint.mutations.isEmpty());assertEquals(1,f.pending(f.layer));
            UploadOperationJournal.Entry unknown=f.creationReceipt();
            assertEquals(UploadOperationJournal.UNCERTAIN,unknown.state);
            assertEquals("legacy_creation_outcome_unknown",unknown.reason);
            assertLegacyRow(f,local,geometry,"raw-226-line");
            String reviewed=f.reviewLegacyCreation(local);assertEquals(unknown.id,reviewed);
            assertTrue(f.layer.authorizeCreationRetry(reviewed));
            assertTrue("Only the reviewed retry may drain the old queue",flushBeforeReplacement(f));
            assertEquals(0,f.pending(f.layer));assertEquals(1,endpoint.mutations.size());
            assertTrue(endpoint.mutations.get(0).startsWith("POST "));
            assertHistoryFreeDurablePayload(f,endpoint.mutations.get(0),reviewed,"create","raw-226-line");
            assertLegacyRow(f,42,geometry,"raw-226-line");
        }
    }
    @Test public void real226AcceptedPostWithTwoIdenticalCandidatesNeedsOperatorSelectedIdAndNeverReposts() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();endpoint.serveFeatures=true;
            long local=f.raw226Line("same-content-is-not-identity");f.reopenLayerFromSaved226Config();
            endpoint.remoteFeatures.put(91,accepted226Line(91,"same-content-is-not-identity"));
            endpoint.remoteFeatures.put(92,accepted226Line(92,"same-content-is-not-identity"));
            assertFalse(f.send());assertFalse(f.send());
            assertTrue(endpoint.mutations.isEmpty());assertEquals(1,f.pending(f.layer));
            assertEquals(2,endpoint.remoteFeatures.size());
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,local,geometry,"same-content-is-not-identity");
            String reviewed=f.reviewLegacyCreation(local),frozen=f.creationReceipt().payload;
            var candidates=f.layer.findCreationCandidates(reviewed);
            assertEquals(new HashSet<>(Arrays.asList(91L,92L)),new HashSet<>(candidates.serverIds));
            assertFalse(candidates.truncated);assertTrue(endpoint.mutations.isEmpty());
            assertEquals(1,f.pending(f.layer));assertLegacyRow(f,local,geometry,"same-content-is-not-identity");
            assertTrue(f.layer.reconcileCreatedFeature(reviewed,91));
            assertTrue(endpoint.mutations.isEmpty());assertEquals(0,f.pending(f.layer));
            assertEquals(frozen,f.creationReceipt().payload);assertEquals(2,endpoint.remoteFeatures.size());
            assertEquals(UploadOperationJournal.CONFIRMED,f.creationReceipt().state);
            assertLegacyRow(f,91,geometry,"same-content-is-not-identity");
        }
    }
    @Test public void real226ReviewAckPreservesAnEditMadeAfterTheFrozenReview() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();endpoint.serveFeatures=true;
            long local=f.raw226Line("frozen-before-review");f.reopenLayerFromSaved226Config();
            endpoint.remoteFeatures.put(91,accepted226Line(91,"frozen-before-review"));
            assertFalse(f.send());String reviewed=f.reviewLegacyCreation(local),frozen=f.creationReceipt().payload;
            f.layer.setSyncDirection(2);ContentValues later=new ContentValues();later.put("name","later-edit-kept");
            assertEquals(1,f.layer.updateAddChanges(later,local));f.layer.setSyncDirection(3);
            assertEquals(2,f.pending(f.layer));assertTrue(f.layer.reconcileCreatedFeature(reviewed,91));
            assertEquals(frozen,f.creationReceipt().payload);assertEquals(1,f.pending(f.layer));assertTrue(endpoint.mutations.isEmpty());
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,91,geometry,"later-edit-kept");
            endpoint.serveFeatures=false;
            assertTrue(f.send());assertEquals(0,f.pending(f.layer));assertEquals(1,endpoint.mutations.size());
            assertTrue(endpoint.mutations.get(0).startsWith("PUT "));
            assertEquals("later-edit-kept",new JSONObject(endpoint.mutations.get(0)
                    .substring(endpoint.mutations.get(0).indexOf('\n')+1)).getJSONObject("fields").getString("name"));
        }
    }
    @Test public void real226ReconciliationMovesPhotosWithFidAndRetainsTheirPendingUpload() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();endpoint.serveFeatures=true;
            long local=f.raw226Line("old-point-with-photo");f.reopenLayerFromSaved226Config();
            File photos=new File(f.layer.getPath(),Long.toString(local));assertTrue(photos.mkdirs());
            byte[] original={2,2,6,0,1};Files.write(new File(photos,"10").toPath(),original);
            f.raw226Change(local,Constants.CHANGE_OPERATION_ATTACH,10,Constants.CHANGE_OPERATION_NEW);
            endpoint.remoteFeatures.put(91,accepted226Line(91,"old-point-with-photo"));
            assertFalse(f.send());assertEquals(2,f.pending(f.layer));assertTrue(endpoint.mutations.isEmpty());
            String reviewed=f.reviewLegacyCreation(local);assertTrue(f.layer.reconcileCreatedFeature(reviewed,91));
            assertEquals(1,f.pending(f.layer));assertTrue(endpoint.mutations.isEmpty());
            assertArrayEquals(original,Files.readAllBytes(new File(f.layer.getPath(),"91/10").toPath()));
            try(Cursor queued=DatabaseContext.getDatabaseForLayer(f.layer,false).query(
                    f.layer.getChangeTableName(),null,null,null,null,null,null)) {
                assertTrue(queued.moveToFirst());assertEquals(91,queued.getLong(queued.getColumnIndexOrThrow(Constants.FIELD_FEATURE_ID)));
                assertEquals(10,queued.getLong(queued.getColumnIndexOrThrow(Constants.FIELD_ATTACH_ID)));assertFalse(queued.moveToNext());
            }
            assertEquals(UploadOperationJournal.CONFIRMED,f.creationReceipt().state);
        }
    }
    @Test public void explicitlyLinked226IdentityKeepsDifferentLocalValuesAndPhotosUntilOnePutDeliversThem() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();endpoint.serveFeatures=true;
            long local=f.raw226Line("retained local offline edit");f.reopenLayerFromSaved226Config();
            File folder=new File(f.layer.getPath(),Long.toString(local));assertTrue(folder.mkdirs());
            byte[] photo={2,2,6,5,9};Files.write(new File(folder,"10").toPath(),photo);
            f.raw226Change(local,Constants.CHANGE_OPERATION_ATTACH,10,Constants.CHANGE_OPERATION_NEW);
            endpoint.remoteFeatures.put(91,accepted226Line(91,"older server value"));
            assertFalse(f.send());String reviewed=f.reviewLegacyCreation(local);
            assertTrue("Identity comes from the operator, not matching business values",f.layer.linkReviewedCreation(reviewed,91));
            assertEquals(2,f.pending(f.layer));assertTrue(endpoint.mutations.isEmpty());
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,91,geometry,"retained local offline edit");
            assertArrayEquals(photo,Files.readAllBytes(new File(f.layer.getPath(),"91/10").toPath()));
            SQLiteDatabase db=DatabaseContext.getDatabaseForLayer(f.layer,false);
            try(Cursor count=db.rawQuery("SELECT count(*) FROM "+f.layer.getPath().getName(),null)) {
                assertTrue(count.moveToFirst());assertEquals(1,count.getInt(0));
            }
            int changed=0;
            try(Cursor queue=db.query(f.layer.getChangeTableName(),null,null,null,null,null,null)) {
                while(queue.moveToNext())if(queue.getInt(queue.getColumnIndexOrThrow(Constants.FIELD_OPERATION))
                        ==Constants.CHANGE_OPERATION_CHANGED) {
                    changed++;assertEquals(91,queue.getLong(queue.getColumnIndexOrThrow(Constants.FIELD_FEATURE_ID)));
                    assertNotNull(queue.getString(queue.getColumnIndexOrThrow(FeatureChanges.OPERATION_UUID)));
                }
            }
            assertEquals(1,changed);endpoint.serveFeatures=false;
            assertFalse("The photo still needs its own review",f.send());
            assertEquals(1,f.pending(f.layer));assertEquals(1,endpoint.mutations.size());
            assertTrue(endpoint.mutations.get(0).startsWith("PUT "));
            assertEquals("retained local offline edit",new JSONObject(endpoint.mutations.get(0)
                    .substring(endpoint.mutations.get(0).indexOf('\n')+1)).getJSONObject("fields").getString("name"));
            assertEquals(0,endpoint.plainFeaturePosts.get());assertEquals(0,endpoint.featureWrites.get());
            assertLegacyRow(f,91,geometry,"retained local offline edit");
            assertArrayEquals(photo,Files.readAllBytes(new File(f.layer.getPath(),"91/10").toPath()));
        }
    }
    @Test public void explicitlyLinked226IdentityRejectsAnExistingLocalTargetWithoutMergingRows() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.resourceMetadata=lisaResourceMetadata().toString();endpoint.serveFeatures=true;
            long local=f.raw226Line("local offline row");f.reopenLayerFromSaved226Config();
            byte[] otherGeometry=GeoGeometryFactory.fromWKT("LINESTRING (8 9, 10 11)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            ContentValues existing=new ContentValues();existing.put(Constants.FIELD_ID,91);existing.put("name","already cached server row");
            existing.put("created_at",1700000000000L);existing.put("created_by","old-client");
            existing.put("business_integer",0);existing.put(Constants.FIELD_GEOM,otherGeometry);
            SQLiteDatabase db=DatabaseContext.getDatabaseForLayer(f.layer,false);
            assertEquals(91,db.insertOrThrow(f.layer.getPath().getName(),null,existing));
            endpoint.remoteFeatures.put(91,accepted226Line(91,"server target"));
            assertFalse(f.send());String reviewed=f.reviewLegacyCreation(local),frozen=f.creationReceipt().payload;
            assertFalse(f.layer.linkReviewedCreation(reviewed,91));
            assertTrue(endpoint.mutations.isEmpty());assertEquals(1,f.pending(f.layer));assertEquals(frozen,f.creationReceipt().payload);
            assertEquals(UploadOperationJournal.UNCERTAIN,f.creationReceipt().state);
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,local,geometry,"local offline row");
            assertLegacyRow(f,91,otherGeometry,"already cached server row");
            try(Cursor count=db.rawQuery("SELECT count(*) FROM "+f.layer.getPath().getName(),null)) {
                assertTrue(count.moveToFirst());assertEquals(2,count.getInt(0));
            }
        }
    }
    private static double[] transactionLineCoordinates(byte[] bytes) {
        assertEquals("Two XY vertices in OGC WKB",41,bytes.length);
        java.nio.ByteBuffer input=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals("Little endian OGC WKB",1,input.get());assertEquals("LineString type without SRID flags",2,input.getInt());
        assertEquals(2,input.getInt());double[] xy=new double[4];
        for(int i=0;i<xy.length;i++)xy[i]=input.getDouble();assertEquals(0,input.remaining());return xy;
    }
    private static void assertTransactionFeatureData(Endpoint endpoint,String name) throws Exception {
        JSONObject operation=endpoint.stagedOperation;assertNotNull(operation);
        assertEquals("feature.create",operation.getString("action"));
        JSONObject fields=operation.getJSONObject("fields");assertEquals(name,fields.getString("name"));
        assertEquals(4,fields.length());assertEquals(0,fields.getInt("business_integer"));
        assertEquals(0L,fields.getLong("business_long_null"));assertEquals(0d,fields.getDouble("business_real_null"),0d);
        for(String field:LISA_HISTORY_FIELDS)assertFalse("Transaction must omit server history: "+field,fields.has(field));
        byte[] bytes=android.util.Base64.decode(operation.getString("geom"),android.util.Base64.DEFAULT);
        double[] xy=transactionLineCoordinates(bytes);
        assertEquals(1d,xy[0],0d);assertEquals(2d,xy[1],0d);assertEquals(3d,xy[2],0d);assertEquals(4d,xy[3],0d);
    }
    @Test public void reviewed226TransactionRecoversLostCommitAndFirstResultAfterLayerReloadWithGetOnly() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.txMode=true;endpoint.resourceMetadata=lisaResourceMetadata().toString();
            long local=f.raw226Line("226 transaction lost responses");f.reopenLayerFromSaved226Config();
            assertFalse(f.send());assertTrue(endpoint.mutations.isEmpty());assertEquals(1,f.pending(f.layer));
            assertEquals("legacy_creation_outcome_unknown",f.creationReceipt().reason);
            String reviewed=f.reviewLegacyCreation(local);f.enableReviewedTransactions(4);
            assertTrue(f.layer.authorizeCreationRetry(reviewed));
            endpoint.dropTransactionCommitResponse=true;endpoint.unavailableTransactionResults.set(1);
            assertFalse("An unknown result keeps the local creation pending",f.send());
            assertEquals(1,f.pending(f.layer));assertEquals(1,endpoint.featureWrites.get());
            assertEquals(0,endpoint.plainFeaturePosts.get());assertEquals(1,endpoint.transactionAllocations.get());
            assertEquals(1,endpoint.transactionStages.get());assertEquals(1,endpoint.transactionCommits.get());
            assertEquals(1,endpoint.transactionResultReads.get());assertEquals(1,endpoint.remoteFeatures.size());
            assertTrue("A stale v4 config must discover the v5 server before reloading the layer",endpoint.versionReads.get()>0);
            assertTransactionFeatureData(endpoint,"226 transaction lost responses");
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,local,geometry,"226 transaction lost responses");
            UploadTransactionJournal.Entry uncertain=UploadTransactionJournal.find(
                    DatabaseContext.getDatabaseForLayer(f.layer,false),f.layer.getPath().getName(),reviewed);
            assertNotNull(uncertain);assertTrue(uncertain.commitStarted);assertEquals(501,uncertain.transactionId);
            assertEquals(UploadTransactionJournal.COMMIT_STARTED,uncertain.phase);
            String frozen=f.creationReceipt().payload;int requestsBeforeReload=endpoint.transactionRequests.size();
            f.reopenSavedLayer();
            assertTrue("The next pass only reads the stored transaction result",f.send());
            assertEquals(0,f.pending(f.layer));assertEquals(1,endpoint.featureWrites.get());
            assertEquals(1,endpoint.transactionAllocations.get());assertEquals(1,endpoint.transactionStages.get());
            assertEquals(1,endpoint.transactionCommits.get());assertEquals(2,endpoint.transactionResultReads.get());
            assertEquals(0,endpoint.plainFeaturePosts.get());assertEquals(1,endpoint.remoteFeatures.size());
            for(String request:endpoint.transactionRequests.subList(requestsBeforeReload,endpoint.transactionRequests.size()))
                assertTrue("No allocation, stage or commit can replay after restart",request.startsWith("GET "));
            assertEquals(frozen,f.creationReceipt().payload);assertEquals(UploadOperationJournal.CONFIRMED,f.creationReceipt().state);
            assertLegacyRow(f,42,geometry,"226 transaction lost responses");
        }
    }
    @Test public void reviewed226ExternalPostgisWriteWithNotCommittedResultNeverReplaysAndNeedsExplicitFidAck() throws Exception {
        try(Endpoint endpoint=new Endpoint();Fixture f=new Fixture(endpoint,true,true)) {
            endpoint.txMode=true;endpoint.transactionNotCommitted=true;endpoint.serveFeatures=true;
            endpoint.resourceMetadata=lisaResourceMetadata().toString();
            long local=f.raw226Line("external PostGIS accepted once");f.reopenLayerFromSaved226Config();
            assertFalse(f.send());assertTrue(endpoint.mutations.isEmpty());
            String reviewed=f.reviewLegacyCreation(local);f.enableReviewedTransactions();
            assertTrue(f.layer.authorizeCreationRetry(reviewed));assertFalse(f.send());
            assertEquals(1,f.pending(f.layer));assertEquals(1,endpoint.featureWrites.get());
            assertEquals(1,endpoint.transactionCommits.get());assertEquals(1,endpoint.remoteFeatures.size());
            assertTransactionFeatureData(endpoint,"external PostGIS accepted once");
            String frozen=f.creationReceipt().payload;int requestsBeforeReload=endpoint.transactionRequests.size();
            f.reopenSavedLayer();assertFalse(f.send());
            assertEquals(1,f.pending(f.layer));assertEquals(1,endpoint.featureWrites.get());
            assertEquals(1,endpoint.transactionAllocations.get());assertEquals(1,endpoint.transactionStages.get());
            assertEquals(1,endpoint.transactionCommits.get());assertEquals(2,endpoint.transactionResultReads.get());
            assertEquals(0,endpoint.plainFeaturePosts.get());
            for(String request:endpoint.transactionRequests.subList(requestsBeforeReload,endpoint.transactionRequests.size()))
                assertTrue("NotCommitted is not permission to repeat an external write",request.startsWith("GET "));
            byte[] geometry=GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",GeoConstants.CRS_WEB_MERCATOR).toBlob();
            assertLegacyRow(f,local,geometry,"external PostGIS accepted once");
            assertEquals(reviewed,f.reviewLegacyCreation(local));
            int mutationsBeforeReview=endpoint.mutations.size();
            assertTrue(f.layer.reconcileCreatedFeature(reviewed,42));
            assertEquals(0,f.pending(f.layer));assertEquals(mutationsBeforeReview,endpoint.mutations.size());
            assertEquals(1,endpoint.featureWrites.get());assertEquals(1,endpoint.transactionCommits.get());
            assertEquals(frozen,f.creationReceipt().payload);assertEquals(UploadOperationJournal.CONFIRMED,f.creationReceipt().state);
            assertLegacyRow(f,42,geometry,"external PostGIS accepted once");
        }
    }
    private void oldQueueBeforeReplacement(boolean legacyServer) throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint, true, true)) {
            endpoint.resourceMetadata = lisaResourceMetadata(legacyServer).toString();
            f.layer.setSyncType(Constants.SYNC_ALL); f.layer.setSyncDirection(2);
            byte[] geometry = GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",
                    GeoConstants.CRS_WEB_MERCATOR).toBlob();
            ContentValues row = new ContentValues(); row.put("name", "old-client-line");
            row.put("created_at", 1700000000000L); row.put("created_by", "old-client");
            row.putNull("updated_at"); row.putNull("updated_by");
            row.put("business_integer", 0); row.putNull("business_long_null"); row.putNull("business_real_null");
            row.put(Constants.FIELD_GEOM, geometry);
            long localId = f.layer.insertAddChanges(row);
            String createId = queuedOperationId(f);
            assertLegacyRow(f, localId, geometry, "old-client-line");
            // The queued row and old saved field configuration predate this client pass.
            f.layer.fromJSON(f.layer.toJSON());
            f.layer.setSyncDirection(3);
            assertTrue("The real rebuild gate must drain old edits before allowing replacement", flushBeforeReplacement(f));
            assertEquals(0L, f.pending(f.layer)); assertEquals(1, endpoint.mutations.size());
            assertHistoryFreeDurablePayload(f, endpoint.mutations.get(0), createId, "create", "old-client-line");
            assertLegacyRow(f, 42, geometry, "old-client-line");
            f.layer.setSyncDirection(2);
            ContentValues edit = new ContentValues(); edit.put("name", "old-client-updated");
            assertEquals(1, f.layer.updateAddChanges(edit, 42));
            String updateId = queuedOperationId(f); f.layer.setSyncDirection(3);
            assertTrue(flushBeforeReplacement(f));
            assertEquals(0L, f.pending(f.layer)); assertEquals(2, endpoint.mutations.size());
            assertTrue(endpoint.mutations.get(1).startsWith("PUT "));
            assertHistoryFreeDurablePayload(f, endpoint.mutations.get(1), updateId, "update", "old-client-updated");
            assertLegacyRow(f, 42, geometry, "old-client-updated");
        }
    }
    @Test public void oldFourFieldQueueDrainsBeforeNewSevenFieldLayerReplacement() throws Exception {
        oldQueueBeforeReplacement(false);
    }
    @Test public void oldFourFieldQueueAlsoDrainsBeforeServerStructureUpdate() throws Exception {
        oldQueueBeforeReplacement(true);
    }
    @Test public void lisaHistoryStaysLocalWhileCreateAndUpdateSendBusinessNumbersFromDurableSnapshots() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint, true)) {
            endpoint.resourceMetadata = lisaResourceMetadata().toString();
            f.layer.setSyncType(Constants.SYNC_ALL); f.layer.setSyncDirection(2);
            ContentValues row = new ContentValues(); row.put("name", "history-line");
            row.put("created_at", 1700000000000L); row.put("created_by", "server-created");
            row.putNull("updated_at"); row.putNull("deleted_at"); row.putNull("updated_by");
            row.putNull("deleted_by"); row.putNull("deletion_operation_id");
            row.put("business_integer", 0); row.putNull("business_long_null"); row.putNull("business_real_null");
            row.put(Constants.FIELD_GEOM, GeoGeometryFactory.fromWKT("LINESTRING (1 2, 3 4)",
                    GeoConstants.CRS_WEB_MERCATOR).toBlob());
            long localId = f.layer.insertAddChanges(row); assertTrue(localId >= 10000000);
            assertLocalHistoryAndNumbers(f, localId);
            String createOperationId = queuedOperationId(f);
            f.layer.setSyncDirection(3); assertTrue(f.layer.save());
            assertTrue("Ordinary local-changes delivery must create the feature", f.send());
            assertEquals(0, f.pending(f.layer)); assertEquals(1, endpoint.mutations.size());
            assertTrue("Authoritative resource metadata must be fetched", endpoint.resourceReads.get() >= 1);
            assertTrue(endpoint.mutations.get(0).startsWith("POST /api/resource/1329/feature/"));
            assertHistoryFreeDurablePayload(f, endpoint.mutations.get(0), createOperationId, "create", "history-line");
            assertLocalHistoryAndNumbers(f, 42);

            f.layer.setSyncDirection(2);
            ContentValues update = new ContentValues(); update.put("name", "history-line-updated");
            assertEquals(1, f.layer.updateAddChanges(update, 42));
            String updateOperationId = queuedOperationId(f);
            assertFalse("The update must have a distinct saved-edit identity", createOperationId.equals(updateOperationId));
            f.layer.setSyncDirection(3);
            int readsBeforeUpdate = endpoint.resourceReads.get();
            assertTrue("Ordinary local-changes delivery must update the feature", f.send());
            assertEquals(0, f.pending(f.layer)); assertEquals(2, endpoint.mutations.size());
            assertTrue("A new delivery pass must fetch its own resource proof",
                    endpoint.resourceReads.get() > readsBeforeUpdate);
            String updateRequestLine = endpoint.mutations.get(1).substring(0, endpoint.mutations.get(1).indexOf('\n'));
            String[] updateRequest = updateRequestLine.split(" ", 3);
            assertEquals(updateRequestLine, 3, updateRequest.length);
            assertEquals(updateRequestLine, "PUT", updateRequest[0]);
            assertEquals(updateRequestLine, "/api/resource/1329/feature/42", URI.create(updateRequest[1]).getPath());
            assertHistoryFreeDurablePayload(f, endpoint.mutations.get(1), updateOperationId, "update", "history-line-updated");
            assertLocalHistoryAndNumbers(f, 42);
        }
    }
    @Test public void savingLineUploadsImmediatelyEvenWhenFullAutoSyncIsDisabledWithoutChangingTime() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long before = ProjectSyncState.lastComplete(f.app, f.path);
            f.savedLine(f.layer, true); f.awaitSent(f.layer);
            assertFalse(ContentResolver.getSyncAutomatically(f.account, f.app.getAuthority()));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
            assertTrue(endpoint.mutations.get(0).startsWith("POST /api/resource/1329/feature/"));
            assertTrue(endpoint.mutations.get(0).contains("LINESTRING"));
            assertEquals(42, f.layer.getFeature(42).getId());
        }
    }
    @Test public void offlineAndRejectedRequestKeepLineUntilRetryThenUpdateAndDeleteAreDelivered() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.savedLine(f.layer, false); long before = ProjectSyncState.lastComplete(f.app, f.path);
            network.connected(false); assertFalse(f.send()); assertEquals(1, f.pending(f.layer)); assertTrue(endpoint.mutations.isEmpty());
            // A definite rejection can be retried; an ambiguous 5xx requires reconciliation.
            network.connected(true); endpoint.status = 403;
            assertFalse(f.send()); assertEquals(1, f.pending(f.layer));
            endpoint.status = 200; assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            f.layer.setSyncDirection(2);
            ContentValues update = new ContentValues(); update.put("name", "changed");
            assertEquals(1, f.layer.updateAddChanges(update, 42)); f.layer.setSyncDirection(3);
            assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            f.layer.setSyncDirection(2); assertEquals(1, f.layer.deleteAddChanges(42)); f.layer.setSyncDirection(3);
            assertTrue(f.send()); assertEquals(0, f.pending(f.layer));
            assertTrue(endpoint.mutations.stream().anyMatch(s -> s.startsWith("PUT ") && s.contains("changed")));
            assertTrue(endpoint.mutations.stream().anyMatch(s -> s.startsWith("DELETE ")));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
        }
    }
    @Test public void temporaryDraftDoesNotUploadUntilSaved() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long id = f.savedLine(f.layer, false); f.layer.setFeatureTempFlag(id, true);
            assertTrue(f.send()); assertTrue(endpoint.mutations.isEmpty());
            f.layer.setFeatureTempFlag(id, false); f.awaitSent(f.layer);
            assertEquals(1, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
        }
    }
    @Test public void commitDuringRunningDeliveryQueuesAnotherPassAndBothLinesReachServer() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            long before = ProjectSyncState.lastComplete(f.app, f.path);
            endpoint.holdRead = true;
            try {
                f.savedLine(f.layer, true);
                assertTrue(endpoint.readEntered.await(30, TimeUnit.SECONDS));
                f.savedLine(f.layer, true);
                boolean following = false; long deadline = android.os.SystemClock.elapsedRealtime() + 10000;
                while (!following && android.os.SystemClock.elapsedRealtime() < deadline) {
                    following = WorkManager.getInstance(f.app).getWorkInfosByTag("local-changes-upload")
                            .get(5, TimeUnit.SECONDS).stream().anyMatch(w -> w.getState() == androidx.work.WorkInfo.State.BLOCKED);
                    if (!following) Thread.sleep(100);
                }
                assertTrue("A commit while the real HTTP pass runs must retain a following job", following);
            } finally { endpoint.releaseRead.countDown(); }
            f.awaitSent(f.layer);
            assertEquals(2, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
            assertNotNull(f.layer.getFeature(42)); assertNotNull(f.layer.getFeature(43));
            assertEquals(before, ProjectSyncState.lastComplete(f.app, f.path));
        }
    }
    @Test public void backgroundProjectWithEqualLayerPathUploadsItsOwnGeometry() throws Exception {
        try (Endpoint endpoint = new Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.savedLine(f.layer, false);
            CollectorProjectRegistry.ProjectInfo project = CollectorProjectRegistry.createLocalProject(f.app, "Delivery background fixture");
            assertNotNull(project);
            MapDrawable background = new MapDrawable(android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888),
                    f.app, new File(project.getMapPath(), project.getMapName() + Constants.MAP_EXT), f.app.getLayerFactory(), false);
            try {
                background.setName(project.getName()); NGWVectorLayerUI own = f.create(background);
                try (SyncWorkspaceSession session = new SyncWorkspaceSession(background); SyncWorkspaceSession.Scope ignored = session.enter()) {
                    f.savedLine(own, false); background.save();
                }
                background.closeSyncWorkspace();
                assertTrue(LocalChangesWorker.uploadProject(f.app, project.getMapPath()));
                assertSame(f.map, f.app.getMap()); assertSame(f.map, MapBase.getActiveInstance());
                assertEquals("The open project's identical layer must stay pending", 1, f.pending(f.layer));
                assertEquals(1, endpoint.mutations.stream().filter(s -> s.startsWith("POST ")).count());
                assertEquals(0, ProjectSyncState.lastComplete(f.app, project.getMapPath()));
            } finally {
                background.closeSyncWorkspace();
                FileUtil.deleteRecursive(new File(project.getMapPath()));
                if (f.registryBefore == null) f.registry.delete(); else Files.write(f.registry.toPath(), f.registryBefore);
            }
        }
    }
}
