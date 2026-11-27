package com.nextgis.mobile.util;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.IProgressor;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.RemoteTMSLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Installs bundled tiles without doing the archive I/O on the application main thread. */
public final class DefaultBasemapInitializer {
    interface MainDispatcher {
        void post(Runnable task);
        void later(Runnable task);
    }

    interface Lease extends AutoCloseable {
        boolean awaitAccess();
        void heartbeat();
        @Override void close();
    }

    interface Work {
        boolean ownsDestination();
        Lease acquire();
        void extract(AtomicBoolean cancelled) throws Exception;
        void publish() throws Exception;
        void cleanup() throws Exception;
        default int extractedFiles() { return 0; }
        default int publishedRoots() { return 0; }
        default int archiveProgressRevision() { return 0; }
    }

    public static final class Snapshot {
        public final boolean scheduled, completed, succeeded, cancelled, workerWasMainThread;
        public final boolean mainHeartbeatDuringExtraction, stagingCleaned;
        public final int extractedFiles, publishedRoots, archiveProgressRevision;
        public final String failureType;

        private Snapshot(Task task) {
            scheduled = task != null;
            completed = task == null || task.done;
            succeeded = task != null && task.done && !task.cancelled.get() && task.failure == null;
            cancelled = task != null && task.cancelled.get();
            workerWasMainThread = task != null && task.workerWasMainThread;
            mainHeartbeatDuringExtraction = task != null && task.mainHeartbeatDuringExtraction;
            stagingCleaned = task == null || task.stagingCleaned;
            extractedFiles = task == null ? 0 : task.work.extractedFiles();
            publishedRoots = task == null ? 0 : task.work.publishedRoots();
            archiveProgressRevision = task == null ? 0 : task.work.archiveProgressRevision();
            failureType = task == null || task.failure == null ? null : task.failure.getClass().getSimpleName();
        }
    }

    private static final class Task {
        final Work work;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final long acquisitionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        volatile boolean extracting, workerWasMainThread, mainHeartbeatDuringExtraction;
        boolean finishing, done, stagingCleaned;
        Exception failure;
        Lease lease;
        Task(Work work) { this.work = work; }
    }

    private final Object lock = new Object();
    private final Executor worker;
    private final MainDispatcher main;
    private int pending;
    private Task latest;

    public DefaultBasemapInitializer() {
        this(Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "DefaultBasemapInitializer");
            thread.setDaemon(true);
            return thread;
        }), dispatcher());
    }

    DefaultBasemapInitializer(Executor worker, MainDispatcher main) {
        this.worker = worker;
        this.main = main;
    }

    private static MainDispatcher dispatcher() {
        Handler handler = new Handler(Looper.getMainLooper());
        return new MainDispatcher() {
            @Override public void post(Runnable task) {
                if (!handler.post(task)) throw new RejectedExecutionException("Main looper is unavailable");
            }
            @Override public void later(Runnable task) {
                if (!handler.postDelayed(task, 100)) throw new RejectedExecutionException("Main looper is unavailable");
            }
        };
    }

    public void initialize(Context context, MapBase owner, RemoteTMSLayer layer, Uri archive) {
        schedule(new ArchiveWork(context.getApplicationContext(), owner, layer, archive));
    }

    void schedule(Work work) {
        Task task = new Task(work);
        synchronized (lock) { latest = task; pending++; }
        try { main.post(() -> begin(task)); }
        catch (RuntimeException failure) { finish(task, failure); }
    }

    private void begin(Task task) {
        try { beginOwned(task); }
        catch (RuntimeException failure) { finish(task, failure); }
    }

    private void beginOwned(Task task) {
        if (!task.work.ownsDestination()) { task.cancelled.set(true); finish(task, null); return; }
        task.lease = task.work.acquire();
        if (task.lease == null) {
            if (System.nanoTime() >= task.acquisitionDeadline) {
                finish(task, new IOException("Default basemap workspace remained busy"));
            } else {
                try { main.later(() -> begin(task)); }
                catch (RuntimeException failure) { finish(task, failure); }
            }
            return;
        }
        try { worker.execute(() -> extract(task)); }
        catch (RuntimeException failure) { finish(task, failure); }
    }

    private void extract(Task task) {
        Exception failure = null;
        try {
            if (!task.lease.awaitAccess()) task.cancelled.set(true);
            if (!task.cancelled.get()) {
                task.workerWasMainThread = Looper.myLooper() == Looper.getMainLooper();
                if (task.workerWasMainThread) throw new IllegalStateException("Archive extraction cannot run on main");
                task.extracting = true;
                main.post(() -> {
                    if (task.extracting) task.mainHeartbeatDuringExtraction = true;
                });
                task.work.extract(task.cancelled);
                task.lease.heartbeat();
            }
        } catch (Exception error) { failure = error; }
        finally { task.extracting = false; }
        Exception extractedFailure = failure;
        try { main.post(() -> publish(task, extractedFailure)); }
        catch (RuntimeException error) { cleanup(task, error); }
    }

    private void publish(Task task, Exception failure) {
        try {
            if (failure == null && !task.cancelled.get()) {
                if (!task.work.ownsDestination()) task.cancelled.set(true);
                else task.work.publish();
            }
        } catch (Exception error) { failure = error; }
        Exception publicationFailure = failure;
        try { worker.execute(() -> cleanup(task, publicationFailure)); }
        catch (RuntimeException error) {
            // The owned stage is retained if the executor cannot finalize it; never unzip on main.
            finish(task, publicationFailure == null ? error : publicationFailure);
        }
    }

    private void cleanup(Task task, Exception failure) {
        try { task.work.cleanup(); task.stagingCleaned = true; }
        catch (Exception error) { if (failure == null) failure = error; }
        Exception finalFailure = failure;
        try { main.post(() -> finish(task, finalFailure)); }
        catch (RuntimeException error) { finish(task, finalFailure == null ? error : finalFailure); }
    }

    private void finish(Task task, Exception failure) {
        synchronized (lock) {
            if (task.finishing) return;
            task.finishing = true;
        }
        try { if (task.lease != null) task.lease.close(); }
        catch (RuntimeException closeFailure) { if (failure == null) failure = closeFailure; }
        synchronized (lock) {
            task.failure = failure;
            task.done = true;
            pending--;
            lock.notifyAll();
        }
        if (failure != null) HyperLog.w(Constants.TAG, "Default basemap initialization failed", failure);
    }

    /** Called by the instrumentation thread, never by the application main thread. */
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Cannot wait for startup on main");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (lock) {
            while (pending != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            }
            return true;
        }
    }

    public Snapshot snapshot() { synchronized (lock) { return new Snapshot(latest); } }

    private static final class ExtractingLayer extends RemoteTMSLayer {
        ExtractingLayer(Context context, File directory) { super(context, directory); }
        void extract(Uri archive, IProgressor progress) throws Exception { fillFromZipInt(archive, progress); }
    }

    private static final class ArchiveWork implements Work {
        final Context context;
        final MapBase owner;
        final RemoteTMSLayer layer;
        final Uri archive;
        final File ownerPath, destination, stage;
        final String originalUrl;
        final int originalTmsType;
        int files, roots;
        volatile int archiveProgressRevision;
        boolean stageCreated;

        ArchiveWork(Context context, MapBase owner, RemoteTMSLayer layer, Uri archive) {
            this.context = context; this.owner = owner; this.layer = layer; this.archive = archive;
            ownerPath = owner.getPath().getAbsoluteFile(); destination = layer.getPath().getAbsoluteFile();
            stage = new File(ownerPath, ".default-basemap-" + UUID.randomUUID());
            originalUrl = layer.getURL(); originalTmsType = layer.getTMSType();
        }

        @Override public boolean ownsDestination() {
            return ((IGISApplication) context).getMap() == owner && layer.getParent() == owner
                    && owner.getLayerById(layer.getId()) == layer && layer.getPath().getAbsoluteFile().equals(destination)
                    && owner.getPath().getAbsoluteFile().equals(ownerPath)
                    && java.util.Objects.equals(originalUrl, layer.getURL()) && originalTmsType == layer.getTMSType();
        }

        @Override public Lease acquire() {
            ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(context,
                    ProjectOperationCoordinator.Kind.LAYER_FILL);
            if (lease == null) return null;
            return new Lease() {
                @Override public boolean awaitAccess() { return lease.awaitDatabaseAccess(); }
                @Override public void heartbeat() { lease.heartbeat(); }
                @Override public void close() { lease.close(); }
            };
        }

        @Override public void extract(AtomicBoolean cancelled) throws Exception {
            if (!stage.getCanonicalFile().getParentFile().equals(ownerPath.getCanonicalFile()))
                throw new IOException("Default basemap stage escaped its workspace");
            Files.createDirectory(stage.toPath()); stageCreated = true;
            new ExtractingLayer(context, stage).extract(archive, new IProgressor() {
                @Override public void setMax(int max) { }
                // TMS reports cumulative compressed bytes. A revision records callback progress,
                // including callbacks whose byte value is unchanged; it is not an entry count.
                @Override public void setValue(int value) { archiveProgressRevision++; }
                @Override public void setIndeterminate(boolean indeterminate) { }
                @Override public void setMessage(String message) { }
                @Override public boolean isCanceled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
            });
            try (Stream<Path> entries = Files.walk(stage.toPath())) { files = (int) entries.filter(Files::isRegularFile).count(); }
        }

        @Override public void publish() throws Exception {
            File[] children = stage.listFiles();
            if (children == null) throw new IOException("Cannot inspect default basemap stage");
            for (File child : children) {
                if (Files.isSymbolicLink(child.toPath()) || !(child.getName().matches("[0-9]+") || child.getName().equals("tiles.json")))
                    throw new IOException("Unexpected default basemap archive root");
            }
            Files.createDirectories(destination.toPath());
            for (File child : children) {
                Path target = new File(destination, child.getName()).toPath();
                if (Files.exists(target)) continue;
                try { Files.move(child.toPath(), target); roots++; }
                catch (FileAlreadyExistsException existingCache) { /* Preserve tiles downloaded meanwhile. */ }
            }
            if (!layer.save()) throw new IOException("Cannot persist initialized default basemap");
        }

        @Override public void cleanup() throws Exception {
            if (!stageCreated || !stage.exists()) return;
            if (!stage.getCanonicalFile().getParentFile().equals(ownerPath.getCanonicalFile()))
                throw new IOException("Default basemap stage identity changed");
            List<Path> entries = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(stage.toPath())) {
                paths.forEach(path -> {
                    if (Files.isSymbolicLink(path)) throw new IllegalStateException("Unexpected link in owned basemap stage");
                    entries.add(path);
                });
            }
            entries.sort(Comparator.reverseOrder());
            for (Path entry : entries) Files.delete(entry);
        }

        @Override public int extractedFiles() { return files; }
        @Override public int publishedRoots() { return roots; }
        @Override public int archiveProgressRevision() { return archiveProgressRevision; }
    }
}
