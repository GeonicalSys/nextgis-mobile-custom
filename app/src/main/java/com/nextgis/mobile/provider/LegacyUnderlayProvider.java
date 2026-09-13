package com.nextgis.mobile.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.LocalTMSLayer;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.MbTilesInfo;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.mobile.util.LegacyUnderlayMigrationContract;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Streams a granted debug underlay as one pipe; it never exposes the debug filesystem path. */
public class LegacyUnderlayProvider extends ContentProvider {
    private static final int BUFFER_SIZE = 64 * 1024;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return LegacyUnderlayMigrationContract.MIME_TYPE;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !LegacyUnderlayMigrationContract.isDebugSource(getContext())) {
            throw new FileNotFoundException("Legacy underlay export is unavailable");
        }
        LocalTMSLayer layer = resolveLayer(uri);
        return openPipeHelper(uri, getType(uri), null, layer,
                (output, ignoredUri, ignoredMime, ignoredOptions, streamedLayer) -> {
                    try (FileOutputStream fileOutput =
                                 new FileOutputStream(output.getFileDescriptor())) {
                        writeLayer(streamedLayer, fileOutput);
                    } catch (IOException | JSONException e) {
                        HyperLog.e(Constants.TAG, "Legacy underlay stream failed layerId="
                                + streamedLayer.getId() + ": " + e.getMessage(), e);
                    }
                });
    }

    private LocalTMSLayer resolveLayer(Uri uri) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !"layer".equals(segments.get(0))) {
            throw new FileNotFoundException("Unknown legacy underlay URI");
        }
        final int id;
        try {
            id = Integer.parseInt(segments.get(1));
        } catch (NumberFormatException e) {
            FileNotFoundException failure =
                    new FileNotFoundException("Invalid legacy underlay id");
            failure.initCause(e);
            throw failure;
        }
        MapBase map = ((GISApplication) getContext().getApplicationContext()).getMap();
        ILayer layer = map.getLayerById(id);
        if (!(layer instanceof LocalTMSLayer)) {
            throw new FileNotFoundException("Legacy underlay was not found");
        }
        return (LocalTMSLayer) layer;
    }

    private void writeLayer(LocalTMSLayer layer, FileOutputStream destination)
            throws IOException, JSONException {
        File root = layer.getPayloadDirectory();
        String rootCanonical = root.getCanonicalPath();
        File mbTiles = new File(root, MbTilesInfo.MBTILES_FILENAME);
        boolean directMbTiles = layer.getTMSType() == GeoConstants.TMSTYPE_MBTILES_RASTER
                && MbTilesInfo.isReadyForMapLibre(mbTiles);

        try (DataOutputStream stream = new DataOutputStream(destination)) {
            JSONObject manifest = new JSONObject();
            manifest.put("schema", LegacyUnderlayMigrationContract.SCHEMA_VERSION);
            manifest.put("name", layer.getName());
            manifest.put("visible", layer.isVisible());
            manifest.put("source_tms_type", layer.getTMSType());
            manifest.put("source_key", LegacyUnderlayMigrationContract.sourceKey(layer));
            manifest.put("kind", directMbTiles ? "mbtiles" : "tiles");
            byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
            stream.writeInt(LegacyUnderlayMigrationContract.STREAM_MAGIC);
            stream.writeInt(LegacyUnderlayMigrationContract.SCHEMA_VERSION);
            stream.writeInt(manifestBytes.length);
            stream.write(manifestBytes);

            long itemCount;
            if (directMbTiles) {
                stream.writeByte(LegacyUnderlayMigrationContract.RECORD_MBTILES);
                writeFilePayload(stream, mbTiles);
                itemCount = 1L;
            } else {
                itemCount = writeTileTree(stream, root, rootCanonical, root);
            }
            stream.writeByte(LegacyUnderlayMigrationContract.RECORD_COMPLETE);
            stream.writeLong(itemCount);
            stream.flush();
        }
    }

    private long writeTileTree(
            DataOutputStream stream, File root, String rootCanonical, File current)
            throws IOException {
        String canonical = current.getCanonicalPath();
        if (!canonical.equals(rootCanonical)
                && !canonical.startsWith(rootCanonical + File.separator)) {
            throw new IOException("Legacy tile path escaped its layer directory");
        }
        if (current.isDirectory()) {
            File[] children = current.listFiles();
            if (children == null) {
                throw new IOException("Legacy tile directory is unreadable");
            }
            long count = 0L;
            for (File child : children) {
                count += writeTileTree(stream, root, rootCanonical, child);
            }
            return count;
        }
        if (!current.getName().endsWith(".tile")) {
            return 0L;
        }
        String relative = root.toPath().relativize(current.toPath())
                .toString().replace(File.separatorChar, '/');
        byte[] relativeBytes = relative.getBytes(StandardCharsets.UTF_8);
        stream.writeByte(LegacyUnderlayMigrationContract.RECORD_TILE);
        stream.writeInt(relativeBytes.length);
        stream.write(relativeBytes);
        writeFilePayload(stream, current);
        return 1L;
    }

    private void writeFilePayload(DataOutputStream stream, File source) throws IOException {
        long expected = source.length();
        if (expected < 0L) {
            throw new IOException("Legacy underlay file size is invalid");
        }
        stream.writeLong(expected);
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long remaining = expected;
            while (remaining > 0L) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) {
                    throw new IOException("Legacy underlay file changed during transfer");
                }
                stream.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("Read only");
    }
}
