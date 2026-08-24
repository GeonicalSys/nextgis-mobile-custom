package com.nextgis.mobile.util;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FileUtil;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.LegacyTileMbtilesMath;
import com.nextgis.maplib.util.MbTilesInfo;
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
import java.util.Locale;

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
        File partial = new File(storage, MbTilesInfo.MBTILES_FILENAME + ".partial");
        File destination = new File(storage, MbTilesInfo.MBTILES_FILENAME);
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
                    try (TileDatabaseWriter writer =
                                 new TileDatabaseWriter(partial, manifest.optString("name", ""))) {
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
                if (!layer.save()) {
                    throw new IOException("Cannot save converted underlay configuration");
                }
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
        }
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

    private static final class TileDatabaseWriter implements AutoCloseable {
        private final SQLiteDatabase database;
        private final SQLiteStatement insertTile;
        private final String layerName;
        private int batchCount;
        private int tileCount;
        private int minZoom = Integer.MAX_VALUE;
        private int maxZoom = Integer.MIN_VALUE;
        private double west = Double.POSITIVE_INFINITY;
        private double south = Double.POSITIVE_INFINITY;
        private double east = Double.NEGATIVE_INFINITY;
        private double north = Double.NEGATIVE_INFINITY;
        private String format;
        private boolean finished;

        TileDatabaseWriter(File file, String layerName) {
            this.layerName = layerName;
            database = SQLiteDatabase.openOrCreateDatabase(file, null);
            applyPragma("PRAGMA journal_mode=OFF");
            applyPragma("PRAGMA synchronous=OFF");
            database.execSQL("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT)");
            database.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, "
                    + "tile_row INTEGER, tile_data BLOB, "
                    + "UNIQUE (zoom_level, tile_column, tile_row))");
            insertTile = database.compileStatement(
                    "INSERT OR REPLACE INTO tiles "
                            + "(zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)");
            database.beginTransaction();
        }

        private void applyPragma(String sql) {
            try (Cursor cursor = database.rawQuery(sql, null)) {
                cursor.moveToFirst();
            }
        }

        int getTileCount() {
            return tileCount;
        }

        void addTile(String entryName, byte[] data, int sourceTmsType) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Underlay conversion interrupted");
            }
            TileCoordinate tile = TileCoordinate.parse(entryName, sourceTmsType);
            String detectedFormat = LegacyTileMbtilesMath.detectRasterFormat(data);
            if (detectedFormat == null) {
                throw new IOException("Unsupported raster tile encoding");
            }
            if (format == null) {
                format = detectedFormat;
            } else if (!format.equals(detectedFormat)) {
                throw new IOException("Legacy underlay mixes raster encodings");
            }

            insertTile.clearBindings();
            insertTile.bindLong(1, tile.zoom);
            insertTile.bindLong(2, tile.x);
            insertTile.bindLong(3, tile.tmsY);
            insertTile.bindBlob(4, data);
            insertTile.executeInsert();
            tileCount++;
            batchCount++;
            updateBounds(tile);
            if (batchCount >= SQLITE_BATCH_SIZE) {
                commitBatch(true);
            }
        }

        void finish() throws IOException {
            if (tileCount == 0 || format == null) {
                throw new IOException("Legacy underlay contains no raster tiles");
            }
            commitBatch(false);
            insertTile.close();
            database.beginTransaction();
            try {
                putMetadata("name", layerName == null || layerName.trim().isEmpty()
                        ? "Underlay" : layerName.trim());
                putMetadata("type", "baselayer");
                putMetadata("version", "1.3");
                putMetadata("format", format);
                putMetadata("minzoom", Integer.toString(minZoom));
                putMetadata("maxzoom", Integer.toString(maxZoom));
                putMetadata("bounds", String.format(Locale.US, "%.8f,%.8f,%.8f,%.8f",
                        west, south, east, north));
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
            finished = true;
        }

        private void commitBatch(boolean continueWriting) {
            database.setTransactionSuccessful();
            database.endTransaction();
            batchCount = 0;
            if (continueWriting) {
                database.beginTransaction();
            }
        }

        private void putMetadata(String name, String value) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("value", value);
            database.insertOrThrow("metadata", null, values);
        }

        private void updateBounds(TileCoordinate tile) {
            int dimension = 1 << tile.zoom;
            west = Math.min(west, LegacyTileMbtilesMath.longitudeFromBoundary(tile.x, dimension));
            east = Math.max(east,
                    LegacyTileMbtilesMath.longitudeFromBoundary(tile.x + 1.0, dimension));
            south = Math.min(south,
                    LegacyTileMbtilesMath.latitudeFromTmsBoundary(tile.tmsY, dimension));
            north = Math.max(north,
                    LegacyTileMbtilesMath.latitudeFromTmsBoundary(tile.tmsY + 1.0, dimension));
            minZoom = Math.min(minZoom, tile.zoom);
            maxZoom = Math.max(maxZoom, tile.zoom);
        }

        @Override
        public void close() {
            if (!finished && database.inTransaction()) {
                database.endTransaction();
            }
            if (!finished) {
                insertTile.close();
            }
            database.close();
        }
    }

    private static final class TileCoordinate {
        final int zoom;
        final int x;
        final int tmsY;

        TileCoordinate(int zoom, int x, int tmsY) {
            this.zoom = zoom;
            this.x = x;
            this.tmsY = tmsY;
        }

        static TileCoordinate parse(String entryName, int sourceTmsType) throws IOException {
            String[] parts = entryName.split("/");
            if (parts.length != 3 || !parts[2].endsWith(".tile")) {
                throw new IOException("Unexpected legacy tile path");
            }
            try {
                int zoom = Integer.parseInt(parts[0]);
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2].substring(0, parts[2].length() - 5));
                if (zoom < 0 || zoom > GeoConstants.DEFAULT_MAX_ZOOM) {
                    throw new IOException("Legacy tile zoom is out of range");
                }
                int dimension = 1 << zoom;
                if (x < 0 || x >= dimension || y < 0 || y >= dimension) {
                    throw new IOException("Legacy tile coordinate is out of range");
                }
                int tmsY = LegacyTileMbtilesMath.toTmsRow(zoom, y, sourceTmsType);
                return new TileCoordinate(zoom, x, tmsY);
            } catch (NumberFormatException e) {
                throw new IOException("Legacy tile coordinate is invalid", e);
            }
        }
    }

}
