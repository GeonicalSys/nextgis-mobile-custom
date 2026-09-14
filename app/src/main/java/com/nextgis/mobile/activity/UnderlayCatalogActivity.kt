package com.nextgis.mobile.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.SharedUnderlayCatalog
import com.nextgis.maplib.util.SharedUnderlayStore
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.CollectorProjectRegistry
import com.nextgis.maplibui.util.ProjectOperationCoordinator
import com.nextgis.maplibui.util.SharedUnderlayProjects
import com.nextgis.mobile.R
import com.nextgis.mobile.util.AppUpdateManager
import com.nextgis.mobile.util.DebugCompanionInstaller
import com.nextgis.mobile.util.LegacyUnderlayImporter
import com.nextgis.mobile.util.LegacyUnderlayMigrationContract
import java.util.concurrent.Executors

class UnderlayCatalogActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_DEBUG_UNDERLAYS = 6401
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var progress: ProgressBar
    private lateinit var importButton: Button
    private var assets = emptyList<SharedUnderlayCatalog.Asset>()
    private val choose get() = intent.getBooleanExtra("choose", false)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_underlay_catalog)
        val layout = findViewById<View>(R.id.underlay_catalog_root)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(layout)
        setSupportActionBar(findViewById(com.nextgis.maplibui.R.id.main_toolbar))
        title = getString(if (choose) R.string.underlay_from_catalog else R.string.underlay_catalog)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        progress = findViewById(R.id.underlay_catalog_progress)
        importButton = findViewById(R.id.underlay_import_from_old_app)
        importButton.setOnClickListener { confirmLegacyUnderlayImport() }
        list = findViewById(R.id.underlay_catalog_list)
        list.emptyView = findViewById(R.id.underlay_catalog_empty)
        list.setOnItemClickListener { _, _, position, _ ->
            val asset = assets[position]
            if (choose) work {
                val added = SharedUnderlayProjects.attach(this, asset.id)
                runOnUiThread {
                    Toast.makeText(this, if (added) R.string.underlay_attached else R.string.underlay_already_attached, Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK); finish()
                }
            } else actions(asset)
        }
        updateImportVisibility()
        work { SharedUnderlayProjects.prepare(this); refresh() }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }

    override fun onResume() {
        super.onResume()
        updateImportVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !choose && !AppUpdateManager.isBusyOrPending(this)) {
            DebugCompanionInstaller.resume(this) { confirmLegacyUnderlayImport() }
        }
    }

    @Deprecated("Uses the existing activity result contract in this screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (DebugCompanionInstaller.onActivityResult(this, requestCode) { confirmLegacyUnderlayImport() }) return
        if (requestCode != REQUEST_DEBUG_UNDERLAYS || resultCode != Activity.RESULT_OK) return

        val sources = ArrayList<Uri>()
        data?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(sources::add)
            }
        }
        if (sources.isEmpty()) {
            data?.data?.let(sources::add)
        }
        if (sources.isEmpty()) {
            Toast.makeText(this, R.string.legacy_underlay_import_failed, Toast.LENGTH_LONG).show()
            return
        }

        val lease = ProjectOperationCoordinator.tryBegin(
            this, ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION)
        if (lease == null) {
            Toast.makeText(this, R.string.project_operation_wait, Toast.LENGTH_LONG).show()
            return
        }
        setBusy(true)
        executor.execute {
            val result = try {
                LegacyUnderlayImporter.importAll(this, sources, lease)
            } finally {
                lease.close()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                val message = if (result.isComplete) {
                    getString(
                        R.string.legacy_underlay_import_done,
                        result.imported,
                        result.skipped
                    )
                } else {
                    getString(
                        R.string.legacy_underlay_import_partial,
                        result.imported,
                        result.total
                    )
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun refresh() {
        val catalog = SharedUnderlayStore.catalog(this)
        val rows = catalog.list().filter { it.isReady || (!choose && it.state == SharedUnderlayCatalog.DELETING) }
        val labels = rows.map { asset ->
            val names = SharedUnderlayProjects.usages(this, asset.id)
            val type = if (asset.kind == SharedUnderlayCatalog.MBTILES) "MBTiles" else "NGRc"
            val size = if (asset.size >= 0) Formatter.formatFileSize(this, asset.size) else getString(R.string.underlay_size_pending)
            "${asset.name}\n$type · $size\n" + getString(R.string.underlay_usage, names.size) +
                if (names.isEmpty()) "" else "\n" + names.joinToString(", ")
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            assets = rows
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
            updateImportVisibility()
        }
    }
    private fun actions(asset: SharedUnderlayCatalog.Asset) {
        val options = if (asset.isReady) arrayOf(getString(R.string.underlay_rename), getString(R.string.underlay_delete))
            else arrayOf(getString(R.string.underlay_delete))
        AlertDialog.Builder(this).setTitle(asset.name).setItems(options) { _, which ->
            if (asset.isReady && which == 0) {
                val input = EditText(this).apply { setText(asset.name); setSingleLine() }
                AlertDialog.Builder(this).setTitle(R.string.underlay_rename).setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (input.text.isNotBlank()) work { SharedUnderlayStore.catalog(this).rename(asset.id, input.text.toString()); refresh() }
                    }.show()
            } else work {
                val names = SharedUnderlayProjects.usages(this, asset.id)
                val uses = if (names.isEmpty()) getString(R.string.underlay_unused) else names.joinToString("\n")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    AlertDialog.Builder(this).setTitle(R.string.underlay_delete)
                        .setMessage(getString(R.string.underlay_delete_confirm, asset.name, uses))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.underlay_delete) { _, _ ->
                            work { SharedUnderlayProjects.delete(this, asset.id); refresh() }
                        }.show()
                }
            }
        }.show()
    }
    private fun confirmLegacyUnderlayImport() {
        if (choose) return
        val project = CollectorProjectRegistry.getActiveProject(this)
        if (project == null) {
            Toast.makeText(this, R.string.project_none_active, Toast.LENGTH_LONG).show()
            return
        }
        if (!canMutateProject()) return
        if (!DebugCompanionInstaller.hasExporter(this)) {
            DebugCompanionInstaller.offer(this, true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.underlay_import_from_old_app)
            .setMessage(getString(R.string.legacy_underlay_import_confirmation, project.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.legacy_underlay_import_confirm) { _, _ ->
                try {
                    startActivityForResult(
                        LegacyUnderlayMigrationContract.createExportIntent(),
                        REQUEST_DEBUG_UNDERLAYS
                    )
                } catch (error: RuntimeException) {
                    HyperLog.w(Constants.TAG, "Debug underlay exporter unavailable", error)
                    Toast.makeText(
                        this,
                        R.string.legacy_underlay_import_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
    private fun updateImportVisibility() {
        if (!::importButton.isInitialized) return
        importButton.visibility = if (
            !choose
            && LegacyUnderlayMigrationContract.isGeonicalTarget(this)
            && LegacyUnderlayMigrationContract.isTrustedDebugSourceInstalled(this)
        ) View.VISIBLE else View.GONE
    }
    private fun canMutateProject(): Boolean {
        if (TrackerService.isTrackerServiceRunning(this)) {
            Toast.makeText(this, R.string.collector_project_switch_tracking, Toast.LENGTH_LONG).show()
            return false
        }
        if (ProjectOperationCoordinator.isBusy()) {
            Toast.makeText(this, R.string.project_operation_wait, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
    private fun setBusy(busy: Boolean) {
        list.isEnabled = !busy
        importButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun work(action: () -> Unit) {
        val lease = ProjectOperationCoordinator.tryBegin(this, ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION)
        if (lease == null) { Toast.makeText(this, R.string.project_operation_wait, Toast.LENGTH_LONG).show(); return }
        setBusy(true)
        executor.execute {
            try { action() }
            catch (error: Exception) {
                HyperLog.w(Constants.TAG, "Shared underlay operation failed", error)
                runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this, R.string.underlay_operation_failed, Toast.LENGTH_LONG).show() }
            } finally {
                lease.close()
                runOnUiThread { if (!isDestroyed) setBusy(false) }
            }
        }
    }
}
