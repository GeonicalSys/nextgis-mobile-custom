package com.nextgis.mobile.util;

import android.content.Context;
import android.net.Uri;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.MbTilesInfo;
import com.nextgis.maplib.util.RasterMbtilesWriter;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.LocalTMSLayerUI;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Converts streamed debug tile directories to one MBTiles database in the active project. */
public final class LegacyUnderlayImporter {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_MANIFEST_SIZE = 64 * 1024;
    private static final int MAX_TILE_SIZE = 16 * 1024 * 1024;
    private static final int SQLITE_BATCH_SIZE = 1000;

    public static final class Result {
        public final int total;
        public final int imported;
        public final int skipped;
        public final int failed;

        private Result(int total, int imported, int skipped, int failed) {
            this.total = total;
            this.imported = imported;
            this.skipped = skipped;
            this.failed = failed;
        }

        public boolean isComplete() {
            return failed == 0 && imported + skipped == total;
        }
    }

    private LegacyUnderlayImporter() {
    }

    public static Result importAll(
            Context context,
            List<Uri> sources,
            ProjectOperationCoordinator.Lease lease) {
        MapBase map = ((GISApplication) context.getApplicationContext()).getMap();
        int insertAt = 0;
        ILayer osm = map.getLayerByPathName("osm");
        if (osm != null) {
            int osmIndex = map.getChildLayerIndex(osm);
            insertAt = osmIndex >= 0 ? osmIndex + 1 : 0;
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        for (Uri source : sources) {
            if (Thread.currentThread().isInterrupted()) {
                failed += sources.size() - imported - skipped - failed;
                break;
            }
            String advertisedKey = source.getQueryParameter("source_key");
            if (hasImportedSource(map, advertisedKey)) {
                skipped++;
                continue;
            }
            try {
                ImportOutcome outcome = importOne(context, map, source, advertisedKey, insertAt, lease);
                if (outcome == ImportOutcome.SKIPPED) {
                    skipped++;
                } else {
                    imported++;
                    insertAt++;
                }
            } catch (IOException | JSONException | RuntimeException e) {
                failed++;
                HyperLog.e(Constants.TAG, "Legacy underlay import failed: " + e.getMessage(), e);
            }
        }
        return new Result(sources.size(), imported, skipped, failed);
    }

    private static ImportOutcome importOne(
            Context context,
            MapBase map,
            Uri source,
            String advertisedKey,
            int insertAt,
            ProjectOperationCoordinator.Lease lease) throws IOException, JSONException {
        File storage = map.createLayerStorage();
        com.nextgis.maplib.util.SharedUnderlayCatalog catalog = com.nextgis.maplib.util.SharedUnderlayStore.catalog(context);
        File stage = catalog.createStage();
        File payload = new File(stage, "payload");
        File partial = new File(payload, MbTilesInfo.MBTILES_FILENAME + ".partial");
        File destination = new File(payload, MbTilesInfo.MBTILES_FILENAME);
        boolean inserted = false;
        LocalTMSLayerUI layer = null;
        try (InputStream opened = context.getContentResolver().openInputStream(source)) {
            if (opened == null) {
                throw new IOException("Debug underlay stream is unavailable");
            }
            try (DataInputStream stream = new DataInputStream(
                    new BufferedInputStream(opened, BUFFER_SIZE))) {
                if (stream.readInt() != LegacyUnderlayMigrationContract.STREAM_MAGIC
                        || stream.readInt() != LegacyUnderlayMigrationContract.SCHEMA_VERSION) {
                    throw new IOException("Unsupported debug underlay schema");
                }
                int manifestLength = stream.readInt();
                JSONObject manifest = new JSONObject(new String(
                        readExactBytes(stream, manifestLength, MAX_MANIFEST_SIZE),
                        StandardCharsets.UTF_8));
                if (manifest.optInt("schema", -1)
                        != LegacyUnderlayMigrationContract.SCHEMA_VERSION) {
                    throw new IOException("Unsupported debug underlay manifest");
                }
                String sourceKey = manifest.optString("source_key", "");
                if (sourceKey.isEmpty()
                        || (advertisedKey != null && !advertisedKey.equals(sourceKey))) {
                    throw new IOException("Debug underlay identity mismatch");
                }
                if (hasImportedSource(map, sourceKey)) {
                    FileUtil.deleteRecursive(storage);
                    return ImportOutcome.SKIPPED;
                }

                com.nextgis.maplib.util.SharedUnderlayCatalog.Asset previous = catalog.find(null, sourceKey);
                if (previous != null) {
                    try {
                        boolean added = com.nextgis.maplibui.util.SharedUnderlayProjects.attach(context, previous.id);
                        FileUtil.deleteRecursive(storage);
                        return added ? ImportOutcome.IMPORTED : ImportOutcome.SKIPPED;
                    } catch (Exception e) { throw new IOException("Cannot attach previously transferred underlay", e); }
                }

                String kind = manifest.optString("kind", "");
                boolean databaseReceived = false;
                if ("mbtiles".equals(kind)) {
                    if (stream.readUnsignedByte()
                            != LegacyUnderlayMigrationContract.RECORD_MBTILES) {
                        throw new IOException("Debug MBTiles payload is missing");
                    }
                    copyExactPayload(stream, partial, stream.readLong());
                    requireCompleteRecord(stream, 1L);
                    databaseReceived = true;
                } else if ("tiles".equals(kind)) {
                    int sourceTmsType = manifest.optInt("source_tms_type", -1);
                    if (sourceTmsType != GeoConstants.TMSTYPE_NORMAL
                            && sourceTmsType != GeoConstants.TMSTYPE_OSM) {
                        throw new IOException("Unsupported legacy tile scheme");
                    }
                    try (RasterMbtilesWriter writer =
                                 new RasterMbtilesWriter(partial, manifest.optString("name", ""))) {
                        while (true) {
                            final int record;
                            try {
                                record = stream.readUnsignedByte();
                            } catch (EOFException e) {
                                throw new IOException("Debug underlay stream ended early", e);
                            }
                            if (record == LegacyUnderlayMigrationContract.RECORD_COMPLETE) {
                                long advertisedCount = stream.readLong();
                                if (advertisedCount != writer.getTileCount()) {
                                    throw new IOException("Debug underlay tile count mismatch");
                                }
                                break;
                            }
                            if (record != LegacyUnderlayMigrationContract.RECORD_TILE) {
                                throw new IOException("Unexpected debug underlay record");
                            }
                            int pathLength = stream.readInt();
                            String path = new String(
                                    readExactBytes(stream, pathLength, 4096),
                                    StandardCharsets.UTF_8);
                            byte[] data = readExactBytes(
                                    stream, stream.readLong(), MAX_TILE_SIZE);
                            writer.addTile(path, data, sourceTmsType);
                            if (lease != null
                                    && writer.getTileCount() % SQLITE_BATCH_SIZE == 0) {
                                lease.heartbeat();
                            }
                        }
                        writer.finish();
                        databaseReceived = true;
                    }
                } else {
                    throw new IOException("Unknown debug underlay kind");
                }

                if (!databaseReceived || stream.read() != -1) {
                    throw new IOException("Debug underlay stream is incomplete");
                }

                syncFile(partial);
                MbTilesInfo info = MbTilesInfo.inspect(partial);
                if (!info.valid) {
                    throw new IOException("Converted MBTiles validation failed: " + info.diagnostic);
                }
                if (!partial.renameTo(destination)) {
                    throw new IOException("Cannot publish converted MBTiles database");
                }

                layer = new LocalTMSLayerUI(context, storage);
                String name = manifest.optString("name", "").trim();
                layer.setName(name.isEmpty() ? "Underlay" : name);
                layer.setVisible(manifest.optBoolean("visible", true));
                try {
                    layer.configureAsRasterMbTiles(info);
                } catch (Exception e) {
                    throw new IOException("Cannot configure converted MBTiles layer", e);
                }
                layer.setLegacyUnderlayMigrationProvenance(
                        LegacyUnderlayMigrationContract.DEBUG_PACKAGE, sourceKey);
                String hash;
                try (InputStream input = new java.io.FileInputStream(destination)) {
                    hash = com.nextgis.maplib.util.UnderlayFiles.sha256(input);
                }
                com.nextgis.maplib.util.SharedUnderlayCatalog.Asset asset = catalog.publish(stage,
                        layer.getName(), com.nextgis.maplib.util.SharedUnderlayCatalog.MBTILES,
                        hash, destination.length(), layer.toJSON(), sourceKey);
                if (com.nextgis.maplibui.util.SharedUnderlayProjects.contains(map, asset.id)) {
                    FileUtil.deleteRecursive(storage);
                    return ImportOutcome.SKIPPED;
                }
                com.nextgis.maplib.util.SharedUnderlayStore.attach(layer, asset);
                map.insertLayer(Math.min(insertAt, map.getLayerCount()), layer);
                inserted = true;
                if (!map.save()) {
                    throw new IOException("Cannot save active project after underlay import");
                }
            }
        } catch (IOException | JSONException | RuntimeException e) {
            if (inserted && layer != null) {
                map.removeLayer(layer);
                map.save();
            }
            FileUtil.deleteRecursive(storage);
            throw e;
        } finally { if (stage.exists()) catalog.discardStage(stage); }
        return ImportOutcome.IMPORTED;
    }

    private static boolean hasImportedSource(MapBase map, String sourceKey) {
        if (sourceKey == null || sourceKey.isEmpty()) {
            return false;
        }
        ArrayList<ILayer> underlays = new ArrayList<>();
        LayerGroup.getLayersByType(map, Constants.LAYERTYPE_LOCAL_TMS, underlays);
        for (ILayer candidate : underlays) {
            if (candidate instanceof LocalTMSLayer
                    && ((LocalTMSLayer) candidate).hasLegacyUnderlayMigrationProvenance(
                    LegacyUnderlayMigrationContract.DEBUG_PACKAGE, sourceKey)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readExactBytes(DataInputStream input, long length, int limit)
            throws IOException {
        if (length < 0L || length > limit) {
            throw new IOException("Debug underlay record is too large");
        }
        byte[] value = new byte[(int) length];
        input.readFully(value);
        return value;
    }

    private static void copyExactPayload(DataInputStream input, File target, long length)
            throws IOException {
        if (length <= 0L) {
            throw new IOException("Debug MBTiles payload is empty");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = length;
            while (remaining > 0L) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Underlay transfer interrupted");
                }
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new EOFException("Debug MBTiles payload ended early");
                }
                output.write(buffer, 0, count);
                remaining -= count;
            }
            output.flush();
        }
    }

    private static void requireCompleteRecord(DataInputStream input, long expectedCount)
            throws IOException {
        if (input.readUnsignedByte() != LegacyUnderlayMigrationContract.RECORD_COMPLETE
                || input.readLong() != expectedCount) {
            throw new IOException("Debug underlay completion record is invalid");
        }
    }

    private static void syncFile(File file) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.getFD().sync();
        }
    }

    private enum ImportOutcome { IMPORTED, SKIPPED }

}
