package com.nextgis.mobile.util;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Converts streamed debug tile directories to one MBTiles database in the active project. */
public final class LegacyUnderlayImporter {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_MANIFEST_SIZE = 64 * 1024;
    private static final int MAX_TILE_SIZE = 16 * 1024 * 1024;
    private static final int SQLITE_BATCH_SIZE = 1000;
    private static final long WATCHDOG_INTERVAL_MS = 5_000L;
    private static final long PROGRESS_REPORT_INTERVAL_MS = 1_000L;
    private static final long LOG_REPORT_INTERVAL_BYTES = 16L * 1024L * 1024L;
    private static final LegacyUnderlayTransferPolicy TRANSFER_POLICY =
            new LegacyUnderlayTransferPolicy();

    public interface ProgressListener {
        void onProgress(Progress progress);
    }

    public static final class Progress {
        public final int sourceIndex;
        public final int sourceCount;
        public final long bytesRead;
        public final long tilesRead;
        public final boolean retrying;

        private Progress(
                int sourceIndex,
                int sourceCount,
                long bytesRead,
                long tilesRead,
                boolean retrying) {
            this.sourceIndex = sourceIndex;
            this.sourceCount = sourceCount;
            this.bytesRead = bytesRead;
            this.tilesRead = tilesRead;
            this.retrying = retrying;
        }
    }

    public static final class Result {
        public final int total;
        public final int imported;
        public final int skipped;
        public final int failed;
        public final boolean canceled;
        public final boolean stalled;

        private Result(
                int total,
                int imported,
                int skipped,
                int failed,
                boolean canceled,
                boolean stalled) {
            this.total = total;
            this.imported = imported;
            this.skipped = skipped;
            this.failed = failed;
            this.canceled = canceled;
            this.stalled = stalled;
        }

        public boolean isComplete() {
            return !canceled && failed == 0 && imported + skipped == total;
        }
    }

    /** Allows the host Activity to terminate a blocked provider pipe immediately. */
    public static final class TransferControl {
        private final Object lock = new Object();
        private final AtomicBoolean canceled = new AtomicBoolean();
        private CancellationSignal cancellationSignal;
        private ParcelFileDescriptor descriptor;

        public void cancel() {
            canceled.set(true);
            synchronized (lock) {
                if (cancellationSignal != null) {
                    cancellationSignal.cancel();
                }
                closeQuietly(descriptor);
            }
        }

        public boolean isCanceled() {
            return canceled.get();
        }

        private boolean attach(
                CancellationSignal signal,
                ParcelFileDescriptor activeDescriptor) {
            synchronized (lock) {
                cancellationSignal = signal;
                descriptor = activeDescriptor;
                if (canceled.get()) {
                    cancellationSignal.cancel();
                    closeQuietly(descriptor);
                    return false;
                }
                return true;
            }
        }

        private void detach(ParcelFileDescriptor activeDescriptor) {
            synchronized (lock) {
                if (descriptor == activeDescriptor) {
                    descriptor = null;
                    cancellationSignal = null;
                }
            }
        }

        private static void closeQuietly(ParcelFileDescriptor value) {
            if (value == null) {
                return;
            }
            try {
                value.close();
            } catch (IOException ignored) {
                // Closing is best effort and only used to unblock a read.
            }
        }
    }

    private LegacyUnderlayImporter() {
    }

    public static Result importAll(
            Context context,
            List<Uri> sources,
            ProjectOperationCoordinator.Lease lease) {
        return importAll(context, sources, lease, new TransferControl(), null);
    }

    public static Result importAll(
            Context context,
            List<Uri> sources,
            ProjectOperationCoordinator.Lease lease,
            TransferControl control,
            ProgressListener listener) {
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
        boolean canceled = false;
        boolean stalled = false;
        TransferControl activeControl = control != null ? control : new TransferControl();
        for (int sourceOffset = 0; sourceOffset < sources.size(); sourceOffset++) {
            if (Thread.currentThread().isInterrupted() || activeControl.isCanceled()) {
                canceled = true;
                failed = sources.size() - imported - skipped;
                break;
            }
            Uri source = sources.get(sourceOffset);
            String advertisedKey = source.getQueryParameter("source_key");
            if (hasImportedSource(map, advertisedKey)) {
                skipped++;
                continue;
            }
            int stallRetries = 0;
            while (true) {
                notifyProgress(
                        listener,
                        sourceOffset + 1,
                        sources.size(),
                        0L,
                        0L,
                        stallRetries > 0);
                try {
                    ImportOutcome outcome = importOne(
                            context,
                            map,
                            source,
                            advertisedKey,
                            insertAt,
                            lease,
                            activeControl,
                            listener,
                            sourceOffset + 1,
                            sources.size(),
                            stallRetries > 0);
                    if (outcome == ImportOutcome.SKIPPED) {
                        skipped++;
                    } else {
                        imported++;
                        insertAt++;
                    }
                    break;
                } catch (TransferCanceledException e) {
                    canceled = true;
                    failed = sources.size() - imported - skipped;
                    HyperLog.i(Constants.TAG, "Legacy underlay transfer canceled source="
                            + (sourceOffset + 1) + "/" + sources.size());
                    break;
                } catch (TransferStalledException e) {
                    if (TRANSFER_POLICY.canRetry(stallRetries)
                            && !activeControl.isCanceled()) {
                        stallRetries++;
                        HyperLog.w(Constants.TAG, "Legacy underlay transfer stalled; retrying source="
                                + (sourceOffset + 1) + "/" + sources.size()
                                + " retry=" + stallRetries);
                        continue;
                    }
                    stalled = true;
                    failed++;
                    HyperLog.e(Constants.TAG, "Legacy underlay transfer stalled source="
                            + (sourceOffset + 1) + "/" + sources.size(), e);
                    break;
                } catch (IOException | JSONException | RuntimeException e) {
                    failed++;
                    HyperLog.e(Constants.TAG, "Legacy underlay import failed source="
                            + (sourceOffset + 1) + "/" + sources.size()
                            + ": " + e.getMessage(), e);
                    break;
                }
            }
            if (canceled) {
                break;
            }
        }
        return new Result(sources.size(), imported, skipped, failed, canceled, stalled);
    }

    private static ImportOutcome importOne(
            Context context,
            MapBase map,
            Uri source,
            String advertisedKey,
            int insertAt,
            ProjectOperationCoordinator.Lease lease,
            TransferControl control,
            ProgressListener listener,
            int sourceIndex,
            int sourceCount,
            boolean retrying) throws IOException, JSONException {
        File storage = map.createLayerStorage();
        com.nextgis.maplib.util.SharedUnderlayCatalog catalog = com.nextgis.maplib.util.SharedUnderlayStore.catalog(context);
        File stage = catalog.createStage();
        File payload = new File(stage, "payload");
        File partial = new File(payload, MbTilesInfo.MBTILES_FILENAME + ".partial");
        File destination = new File(payload, MbTilesInfo.MBTILES_FILENAME);
        boolean inserted = false;
        LocalTMSLayerUI layer = null;
        TransferSession transfer = new TransferSession(
                lease, control, listener, sourceIndex, sourceCount, retrying);
        try (InputStream opened = transfer.open(context, source)) {
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
                transfer.manifestRead(kind);
                boolean databaseReceived = false;
                if ("mbtiles".equals(kind)) {
                    if (stream.readUnsignedByte()
                            != LegacyUnderlayMigrationContract.RECORD_MBTILES) {
                        throw new IOException("Debug MBTiles payload is missing");
                    }
                    copyExactPayload(stream, partial, stream.readLong());
                    requireCompleteRecord(stream, 1L);
                    requireEndOfStream(stream);
                    transfer.streamComplete();
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
                                requireEndOfStream(stream);
                                transfer.streamComplete();
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
                            transfer.tileRead();
                        }
                        writer.finish();
                        databaseReceived = true;
                    }
                } else {
                    throw new IOException("Unknown debug underlay kind");
                }

                if (!databaseReceived) {
                    throw new IOException("Debug underlay stream is incomplete");
                }

                throwIfCanceled(control);
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
                throwIfCanceled(control);
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
                transfer.importComplete();
            }
        } catch (IOException | JSONException | RuntimeException e) {
            if (inserted && layer != null) {
                map.removeLayer(layer);
                map.save();
            }
            FileUtil.deleteRecursive(storage);
            if (control.isCanceled()) {
                throw new TransferCanceledException(e);
            }
            if (transfer.isTimedOut()) {
                throw new TransferStalledException(e);
            }
            if (e instanceof OperationCanceledException) {
                throw new IOException("Debug underlay stream was canceled", e);
            }
            throw e;
        } finally {
            transfer.close();
            if (stage.exists()) {
                catalog.discardStage(stage);
            }
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

    private static void requireEndOfStream(DataInputStream input) throws IOException {
        if (input.read() != -1) {
            throw new IOException("Debug underlay stream has trailing data");
        }
    }

    private static void throwIfCanceled(TransferControl control)
            throws TransferCanceledException {
        if (control.isCanceled() || Thread.currentThread().isInterrupted()) {
            throw new TransferCanceledException(null);
        }
    }

    private static void syncFile(File file) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.getFD().sync();
        }
    }

    private static void notifyProgress(
            ProgressListener listener,
            int sourceIndex,
            int sourceCount,
            long bytesRead,
            long tilesRead,
            boolean retrying) {
        if (listener != null) {
            listener.onProgress(new Progress(
                    sourceIndex, sourceCount, bytesRead, tilesRead, retrying));
        }
    }

    private static final class TransferSession implements AutoCloseable {
        private final ProjectOperationCoordinator.Lease lease;
        private final TransferControl control;
        private final ProgressListener listener;
        private final int sourceIndex;
        private final int sourceCount;
        private final boolean retrying;
        private final long startedAt = System.currentTimeMillis();
        private final AtomicLong lastProgressAt = new AtomicLong(startedAt);
        private final AtomicLong bytesRead = new AtomicLong();
        private final AtomicLong tilesRead = new AtomicLong();
        private final AtomicBoolean timedOut = new AtomicBoolean();
        private final AtomicBoolean streamComplete = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ScheduledExecutorService watchdog =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "legacy-underlay-watchdog");
                    thread.setDaemon(true);
                    return thread;
                });

        private final CancellationSignal cancellationSignal = new CancellationSignal();
        private volatile ParcelFileDescriptor descriptor;
        private volatile long lastListenerAt;
        private volatile long lastLoggedBytes;
        private volatile long lastHeartbeatAt;

        TransferSession(
                ProjectOperationCoordinator.Lease lease,
                TransferControl control,
                ProgressListener listener,
                int sourceIndex,
                int sourceCount,
                boolean retrying) {
            this.lease = lease;
            this.control = control;
            this.listener = listener;
            this.sourceIndex = sourceIndex;
            this.sourceCount = sourceCount;
            this.retrying = retrying;
            control.attach(cancellationSignal, null);
            watchdog.scheduleAtFixedRate(
                    this::checkForStall,
                    WATCHDOG_INTERVAL_MS,
                    WATCHDOG_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }

        InputStream open(Context context, Uri source) throws IOException {
            HyperLog.i(Constants.TAG, "Legacy underlay transfer open source="
                    + sourceIndex + "/" + sourceCount + " retry=" + retrying);
            if (control.isCanceled()) {
                throw new TransferCanceledException(null);
            }
            try {
                descriptor = context.getContentResolver().openFileDescriptor(
                        source, "r", cancellationSignal);
            } catch (OperationCanceledException e) {
                if (control.isCanceled()) {
                    throw new TransferCanceledException(e);
                }
                if (timedOut.get()) {
                    throw new TransferStalledException(e);
                }
                throw e;
            }
            if (descriptor == null) {
                throw new IOException("Debug underlay descriptor is unavailable");
            }
            if (!control.attach(cancellationSignal, descriptor)) {
                throw new TransferCanceledException(null);
            }
            return new FilterInputStream(new FileInputStream(descriptor.getFileDescriptor())) {
                @Override
                public int read() throws IOException {
                    int value = super.read();
                    if (value >= 0) {
                        onBytesRead(1);
                    }
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = super.read(buffer, offset, length);
                    if (count > 0) {
                        onBytesRead(count);
                    }
                    return count;
                }
            };
        }

        void manifestRead(String kind) {
            HyperLog.i(Constants.TAG, "Legacy underlay transfer manifest source="
                    + sourceIndex + "/" + sourceCount + " kind=" + kind);
        }

        void tileRead() {
            long count = tilesRead.incrementAndGet();
            if (lease != null && count % SQLITE_BATCH_SIZE == 0L) {
                lease.heartbeat();
            }
            reportProgress(false);
        }

        void streamComplete() {
            if (streamComplete.compareAndSet(false, true)) {
                watchdog.shutdownNow();
                reportProgress(true);
                HyperLog.i(Constants.TAG, "Legacy underlay transfer stream complete source="
                        + sourceIndex + "/" + sourceCount
                        + " bytes=" + bytesRead.get()
                        + " tiles=" + tilesRead.get());
            }
        }

        void importComplete() {
            reportProgress(true);
            HyperLog.i(Constants.TAG, "Legacy underlay transfer complete source="
                    + sourceIndex + "/" + sourceCount
                    + " bytes=" + bytesRead.get()
                    + " tiles=" + tilesRead.get()
                    + " elapsedMs=" + (System.currentTimeMillis() - startedAt));
        }

        boolean isTimedOut() {
            return timedOut.get();
        }

        private void onBytesRead(int count) {
            long now = System.currentTimeMillis();
            bytesRead.addAndGet(count);
            lastProgressAt.set(now);
            if (lease != null && now - lastHeartbeatAt >= PROGRESS_REPORT_INTERVAL_MS) {
                lastHeartbeatAt = now;
                lease.heartbeat();
            }
            reportProgress(false);
        }

        private void reportProgress(boolean force) {
            long now = System.currentTimeMillis();
            long currentBytes = bytesRead.get();
            if (!force && now - lastListenerAt < PROGRESS_REPORT_INTERVAL_MS) {
                return;
            }
            lastListenerAt = now;
            notifyProgress(
                    listener,
                    sourceIndex,
                    sourceCount,
                    currentBytes,
                    tilesRead.get(),
                    retrying);
            if (force || currentBytes - lastLoggedBytes >= LOG_REPORT_INTERVAL_BYTES) {
                lastLoggedBytes = currentBytes;
                HyperLog.i(Constants.TAG, "Legacy underlay transfer progress source="
                        + sourceIndex + "/" + sourceCount
                        + " bytes=" + currentBytes
                        + " tiles=" + tilesRead.get());
            }
        }

        private void checkForStall() {
            if (closed.get() || streamComplete.get() || control.isCanceled()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!TRANSFER_POLICY.isStalled(now, lastProgressAt.get())
                    || !timedOut.compareAndSet(false, true)) {
                return;
            }
            HyperLog.w(Constants.TAG, "Legacy underlay transfer inactivity timeout source="
                    + sourceIndex + "/" + sourceCount
                    + " bytes=" + bytesRead.get()
                    + " tiles=" + tilesRead.get()
                    + " inactiveMs=" + (now - lastProgressAt.get()));
            cancellationSignal.cancel();
            TransferControl.closeQuietly(descriptor);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            watchdog.shutdownNow();
            TransferControl.closeQuietly(descriptor);
            control.detach(descriptor);
        }
    }

    private static final class TransferCanceledException extends InterruptedIOException {
        TransferCanceledException(Throwable cause) {
            super("Legacy underlay transfer canceled");
            if (cause != null) {
                initCause(cause);
            }
        }
    }

    private static final class TransferStalledException extends InterruptedIOException {
        TransferStalledException(Throwable cause) {
            super("Legacy underlay transfer stopped responding");
            if (cause != null) {
                initCause(cause);
            }
        }
    }

    private enum ImportOutcome { IMPORTED, SKIPPED }

}
