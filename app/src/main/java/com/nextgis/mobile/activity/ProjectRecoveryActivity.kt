package com.nextgis.mobile.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.map.MapContentProviderHelper
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.UnderlayFiles
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.*
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.R
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipFile

/** Archives saved project state; unfinished forms retain their independent recovery route. */
class ProjectRecoveryActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var runningThread: Thread? = null
    @Volatile private var cancelRequested = false
    private var busy = false
    private var loading = false
    private var hasProject = false
    private var observedBusy = false
    private var exportKind: String? = null
    private var exportId: String? = null
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var archives: LinearLayout
    private lateinit var drafts: LinearLayout
    private val rowButtons = ArrayList<Pair<Button, Boolean>>()
    private val poll = object : Runnable {
        override fun run() {
            val current = ProjectOperationCoordinator.isBusy()
            if (observedBusy && !current && !busy && !loading) refresh()
            observedBusy = current
            updateEnabled()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_project_recovery)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.project_recovery)
        }
        progress = findViewById(R.id.recovery_progress)
        status = findViewById(R.id.recovery_status)
        archives = findViewById(R.id.recovery_archives)
        drafts = findViewById(R.id.recovery_drafts)
        exportKind = state?.getString("export_kind")
        exportId = state?.getString("export_id")
        findViewById<Button>(R.id.recovery_create).setOnClickListener { createArchive() }
        findViewById<Button>(R.id.recovery_import).setOnClickListener {
            if (canChangeProject()) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/zip"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, IMPORT)
        }
        findViewById<Button>(R.id.recovery_cancel).setOnClickListener {
            cancelRequested = true
            runningThread?.interrupt()
            status.setText(R.string.recovery_cancelling)
            updateEnabled()
        }
        findViewById<Button>(R.id.recovery_source_export).setOnClickListener { chooseExport("source", "", "draft-original.zip") }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("export_kind", exportKind)
        state.putString("export_id", exportId)
        super.onSaveInstanceState(state)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun activeWork(): Boolean = TrackerService.isTrackerServiceRunning(this) ||
        WalkSessionStore.load(this) != null || (application as MainApplication).stakeoutSession.isActive

    private fun canChangeProject(): Boolean {
        val allowed = !busy && !loading && !ProjectOperationCoordinator.isBusy() && !activeWork()
        if (!allowed) status.setText(R.string.recovery_wait)
        return allowed
    }

    private fun updateEnabled() {
        if (isDestroyed) return
        val idle = !busy && !loading
        val allowed = idle && hasProject && !ProjectOperationCoordinator.isBusy() && !activeWork()
        findViewById<Button>(R.id.recovery_create).isEnabled = allowed
        findViewById<Button>(R.id.recovery_import).isEnabled = allowed
        findViewById<Button>(R.id.recovery_source_export).isEnabled = idle
        findViewById<Button>(R.id.recovery_cancel).visibility = if (busy) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.recovery_cancel).isEnabled = busy && !cancelRequested
        findViewById<TextView>(R.id.recovery_wait).visibility =
            if (!busy && (!allowed && idle)) View.VISIBLE else View.GONE
        rowButtons.forEach { (button, mutation) -> button.isEnabled = if (mutation) allowed else idle }
    }

    private fun refresh() {
        if (busy || loading || isDestroyed) return
        loading = true
        updateEnabled()
        executor.execute {
            try {
                val project = CollectorProjectRegistry.getActiveProject(this)
                FeatureFormDraftStore.load(this) // preserves an unreadable checkpoint before listing it
                val entries = DraftQuarantineStore.list(this)
                val unreadable = FeatureFormDraftStore.hasUnreadableSource(this)
                val files = ProjectArchiveManager.archiveDirectory(this).listFiles().orEmpty()
                    .filter { it.isFile && it.extension.equals("zip", true) }
                    .sortedByDescending { it.lastModified() }.take(50)
                val labels = files.map { file -> file to archiveLabel(file) }
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    loading = false
                    hasProject = project != null
                    findViewById<TextView>(R.id.recovery_project).text = project?.name ?: getString(R.string.project_none_active)
                    archives.removeAllViews(); drafts.removeAllViews(); rowButtons.clear()
                    if (labels.isEmpty()) addText(archives, getString(R.string.recovery_no_archives))
                    labels.forEach { (file, label) ->
                        addText(archives, label)
                        addButton(archives, R.string.recovery_restore, true) { confirmRestore(file) }
                        addButton(archives, R.string.recovery_export, false) { chooseExport("archive", file.name, file.name) }
                    }
                    findViewById<Button>(R.id.recovery_source_export).visibility = if (unreadable) View.VISIBLE else View.GONE
                    findViewById<TextView>(R.id.recovery_source_problem).visibility = if (unreadable) View.VISIBLE else View.GONE
                    if (entries.isEmpty()) addText(drafts, getString(R.string.recovery_no_drafts))
                    entries.take(50).forEach { entry ->
                        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.created))
                        addText(drafts, getString(R.string.recovery_draft_entry, date, entry.bytes,
                            getString(if (entry.recovered) R.string.recovery_draft_recovered else R.string.recovery_draft_preserved)))
                        addButton(drafts, R.string.recovery_retry_draft, true) {
                            runOperation(true) {
                                if (!FeatureFormDraftStore.retryQuarantined(this, entry.id)) throw IOException("Draft cannot be recovered here")
                                getString(R.string.recovery_draft_ready)
                            }
                        }
                        addButton(drafts, R.string.recovery_export, false) { chooseExport("draft", entry.id, "draft-${entry.id.take(16)}.zip") }
                    }
                    updateEnabled()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        loading = false; status.text = errorText(error); updateEnabled()
                    }
                }
            }
        }
    }

    private fun archiveLabel(file: File): String = try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("manifest.json") ?: throw IOException("Missing manifest")
            val metadata = zip.getInputStream(entry).use {
                JSONObject(String(UnderlayFiles.readBounded(it, 16 * 1024 * 1024), StandardCharsets.UTF_8))
            }
            val source = metadata.getJSONObject("source")
            getString(R.string.recovery_archive_entry, source.getString("name"),
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(metadata.getLong("created"))))
        }
    } catch (error: Exception) { getString(R.string.recovery_archive_unchecked, file.name) }

    private fun addText(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addButton(parent: LinearLayout, caption: Int, mutation: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            setText(caption); isAllCaps = false
            setOnClickListener { if (!busy && !loading) action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2))
        rowButtons.add(button to mutation)
    }

    private fun runOperation(mutation: Boolean, after: (() -> Unit)? = null, action: () -> String) {
        if (busy || (mutation && !canChangeProject())) return
        cancelRequested = false
        busy = true; progress.visibility = View.VISIBLE; status.setText(R.string.recovery_working); updateEnabled()
        executor.execute {
            runningThread = Thread.currentThread()
            var success = false
            val message = try {
                if (cancelRequested) throw InterruptedIOException("Cancelled")
                action().also { success = true }
            }
                catch (error: Exception) { HyperLog.w(Constants.TAG, "Project recovery failed", error); errorText(error) }
                finally { runningThread = null; Thread.interrupted() }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false; progress.visibility = View.GONE; status.text = message
                refresh()
                if (success) after?.invoke()
            }
        }
    }

    private fun createArchive() {
        if (!canChangeProject()) return
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        val map = (application as GISApplication).map as? MapContentProviderHelper ?: return
        runOperation(true) {
            ProjectArchiveManager.create(this, map, project)
            getString(R.string.recovery_archive_created)
        }
    }

    private fun confirmRestore(file: File) {
        if (!canChangeProject()) return
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        FittedDialogs.compatBuilder(this).setTitle(R.string.recovery_restore)
            .setMessage(R.string.recovery_restore_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> restore(file, project) }.show()
    }

    private fun restore(file: File, project: CollectorProjectRegistry.ProjectInfo) {
        var copy: CollectorProjectRegistry.ProjectInfo? = null
        runOperation(true, after = {
            copy?.let { result ->
                FittedDialogs.compatBuilder(this).setTitle(R.string.recovery_copy_created)
                    .setMessage(getString(R.string.recovery_copy_ready, result.name))
                    .setNegativeButton(android.R.string.ok, null)
                    .setPositiveButton(R.string.recovery_open_copy) { _, _ ->
                        openCopy(result)
                    }.show()
            }
        }) {
            copy = ProjectArchiveManager.restore(this, file, project,
                getString(R.string.recovery_copy_name, project.name), MapContentProviderHelper.getCurrentDatabaseVersion())
            getString(R.string.recovery_copy_created)
        }
    }

    private fun openCopy(project: CollectorProjectRegistry.ProjectInfo) {
        if (isDestroyed || isFinishing) return
        if (loading) { handler.postDelayed({ openCopy(project) }, 100); return }
        if (canChangeProject() && CollectorProjectRegistry.activateProject(this, project.projectUid)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish()
        }
    }

    private fun chooseExport(kind: String, id: String, name: String) {
        exportKind = kind; exportId = id
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, name)
        }, EXPORT)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (result != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        if (request == EXPORT) {
            val kind = exportKind; val id = exportId
            exportKind = null; exportId = null
            runOperation(false) {
                contentResolver.openOutputStream(uri, "w")?.use { output ->
                    when (kind) {
                        "archive" -> {
                            val root = ProjectArchiveManager.archiveDirectory(this)
                            val source = File(root, id ?: throw IOException("Missing archive"))
                            UnderlayFiles.requireChild(root, source)
                            if (Files.isSymbolicLink(source.toPath()) || !source.isFile) throw IOException("Missing archive")
                            source.inputStream().use { input -> copyStream(input, output) }
                        }
                        "draft" -> DraftQuarantineStore.export(this, id, output)
                        "source" -> FeatureFormDraftStore.exportUnreadableSource(this, output)
                        else -> throw IOException("No export selected")
                    }
                } ?: throw IOException("Cannot open export destination")
                getString(R.string.recovery_export_done)
            }
        } else if (request == IMPORT) importArchive(uri)
    }

    private fun importArchive(uri: Uri) {
        if (isDestroyed || isFinishing) return
        if (loading) { handler.postDelayed({ importArchive(uri) }, 100); return }
        if (!canChangeProject()) return
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        runOperation(true) {
            val directory = File(cacheDir, "recovery-imports")
            if (Files.isSymbolicLink(directory.toPath())) throw IOException("Invalid import directory")
            UnderlayFiles.directory(directory)
            val temporary = File.createTempFile("incoming-", ".zip", directory)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output -> copyStream(input, output, directory) }
                } ?: throw IOException("Cannot read archive")
                val copy = ProjectArchiveManager.restore(this, temporary, project,
                    getString(R.string.recovery_copy_name, project.name), MapContentProviderHelper.getCurrentDatabaseVersion())
                getString(R.string.recovery_copy_ready, copy.name)
            } finally {
                if (temporary.exists() && !temporary.delete()) throw IOException("Cannot remove owned import staging file")
            }
        }
    }

    private fun copyStream(input: java.io.InputStream, output: java.io.OutputStream, disk: File? = null) {
        val bytes = ByteArray(64 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Cancelled")
            val count = input.read(bytes)
            if (count < 0) return
            if (disk != null && disk.usableSpace < count + 16L * 1024 * 1024) throw IOException("Not enough free space")
            output.write(bytes, 0, count)
        }
    }

    private fun errorText(error: Exception): String {
        val message = generateSequence(error as Throwable?) { it.cause }.mapNotNull { it.message }
            .joinToString(" ").lowercase(Locale.ROOT)
        val text = when {
            error is InterruptedIOException || error is InterruptedException || message.contains("cancel") -> R.string.recovery_cancelled
            message.contains("another") || message.contains("identity") || message.contains("account") -> R.string.recovery_wrong_source
            message.contains("space") || message.contains("enospc") -> R.string.recovery_no_space
            message.contains("memory") -> R.string.recovery_no_memory
            message.contains("busy") -> R.string.recovery_wait
            message.contains("draft cannot") -> R.string.recovery_draft_not_recoverable
            else -> R.string.recovery_failed
        }
        return getString(text)
    }

    companion object { private const val EXPORT = 8101; private const val IMPORT = 8102 }
}
