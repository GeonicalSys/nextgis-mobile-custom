package com.nextgis.mobile.activity

import android.content.ContentResolver
import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FeatureChanges
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.mapui.SyncAccountWorker
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.CollectorProjectRegistry
import com.nextgis.maplibui.util.LayerBackupManager
import com.nextgis.maplibui.util.ProjectOperationCoordinator
import com.nextgis.maplibui.util.SchemaRebuildRetryGuard
import com.nextgis.mobile.R
import com.nextgis.mobile.util.OfflineSyncIntentService
import com.nextgis.mobile.util.LegacyUnderlayImporter
import com.nextgis.mobile.util.LegacyUnderlayMigrationContract
import com.nextgis.mobile.util.DebugCompanionInstaller
import com.nextgis.mobile.util.AppUpdateManager
import android.net.Uri
import java.util.concurrent.Executors

class ProjectSettingsActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_DEBUG_UNDERLAYS = 6401
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var progress: ProgressBar
    private lateinit var actionButtons: List<Button>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_settings)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.project_settings)
        }
        progress = findViewById(R.id.project_progress)
        actionButtons = listOf(
            findViewById(R.id.project_choose),
            findViewById(R.id.project_sync),
            findViewById(R.id.project_create_local),
            findViewById(R.id.project_rename),
            findViewById(R.id.project_reset_rebuild_guard),
            findViewById(R.id.project_import_debug_underlays),
            findViewById(R.id.project_delete)
        )
        findViewById<Button>(R.id.project_choose).setOnClickListener { chooseProject() }
        findViewById<Button>(R.id.project_sync).setOnClickListener { syncCurrentProject() }
        findViewById<Button>(R.id.project_create_local).setOnClickListener { createLocalProject() }
        findViewById<Button>(R.id.project_rename).setOnClickListener { renameProject() }
        findViewById<Button>(R.id.project_reset_rebuild_guard).setOnClickListener {
            SchemaRebuildRetryGuard.clearForWorkspace(
                this, ProjectOperationCoordinator.activeWorkspaceKey(this))
            Toast.makeText(this, R.string.project_rebuild_guard_reset_done, Toast.LENGTH_SHORT).show()
            refresh()
        }
        findViewById<Button>(R.id.project_import_debug_underlays).setOnClickListener {
            confirmDebugUnderlayImport()
        }
        findViewById<Button>(R.id.project_delete).setOnClickListener { confirmDeleteProject() }
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstallation(this)
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !AppUpdateManager.isBusyOrPending(this)) {
            DebugCompanionInstaller.resume(this) { confirmDebugUnderlayImport() }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        val project = CollectorProjectRegistry.getActiveProject(this)
        val hasProject = project != null
        findViewById<TextView>(R.id.project_name).text =
            project?.name ?: getString(R.string.project_none_active)
        findViewById<TextView>(R.id.project_type).text = when {
            project == null -> ""
            project.isLocal -> getString(R.string.project_type_local)
            else -> getString(R.string.project_type_webgis)
        }
        val webDetails = findViewById<LinearLayout>(R.id.project_webgis_details)
        webDetails.visibility = if (project != null && !project.isLocal) View.VISIBLE else View.GONE
        if (project != null && !project.isLocal) {
            findViewById<TextView>(R.id.project_account).text =
                getString(R.string.project_account, project.accountName)
            findViewById<TextView>(R.id.project_remote_id).text =
                getString(R.string.project_remote_id, project.projectRemoteId)
            findViewById<TextView>(R.id.project_district).text = getString(
                R.string.project_district,
                project.district?.takeIf { it.isNotBlank() } ?: getString(R.string.project_value_none)
            )
        }
        val state = collectLayerState()
        findViewById<TextView>(R.id.project_layers).text =
            getString(R.string.project_layer_count, state.layerCount)
        findViewById<TextView>(R.id.project_pending_changes).text =
            getString(R.string.project_pending_layer_count, state.changedLayerCount)
        findViewById<TextView>(R.id.project_operation_status).text = getString(
            R.string.project_operation_status,
            if (ProjectOperationCoordinator.isBusy())
                getString(R.string.project_operation_busy)
            else getString(R.string.project_operation_idle)
        )
        val blocked = if (hasProject) SchemaRebuildRetryGuard.blockedLayerCount(
            this, ProjectOperationCoordinator.activeWorkspaceKey(this)) else 0
        findViewById<TextView>(R.id.project_rebuild_guard).text =
            getString(R.string.project_rebuild_guard_count, blocked)
        findViewById<Button>(R.id.project_reset_rebuild_guard).visibility =
            if (blocked > 0) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.project_sync).visibility =
            if (project != null && !project.isLocal) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.project_rename).isEnabled = hasProject
        findViewById<Button>(R.id.project_delete).isEnabled = hasProject
        findViewById<Button>(R.id.project_choose).isEnabled =
            CollectorProjectRegistry.listProjects(this).size > 1
        findViewById<Button>(R.id.project_import_debug_underlays).visibility =
            if (hasProject
                && LegacyUnderlayMigrationContract.isGeonicalTarget(this)
                && LegacyUnderlayMigrationContract.isTrustedDebugSourceInstalled(this)
            ) View.VISIBLE else View.GONE
    }

    private fun confirmDebugUnderlayImport() {
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        if (!canMutateProject()) return
        if (!DebugCompanionInstaller.hasExporter(this)) {
            DebugCompanionInstaller.offer(this, true)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.project_import_debug_underlays)
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

    @Deprecated("Uses the existing activity result contract in this screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
                if (result.imported > 0) openMap() else refresh()
            }
        }
    }

    private fun chooseProject() {
        val projects = CollectorProjectRegistry.listProjects(this)
        if (projects.isEmpty()) {
            Toast.makeText(this, R.string.collector_projects_empty, Toast.LENGTH_LONG).show()
            return
        }
        ProjectChooserDialog.show(this, projects) { project ->
            if (project.isActive(this)) return@show
            if (!canMutateProject()) return@show
            if (!CollectorProjectRegistry.activateProject(this, project.projectUid)) {
                Toast.makeText(this, R.string.collector_project_switch_busy, Toast.LENGTH_LONG).show()
                return@show
            }
            scheduleAutomaticSync(project)
            openMap()
        }
    }

    private fun createLocalProject() {
        if (!canMutateProject()) return
        showNameDialog(R.string.project_create_local, getString(R.string.project_local_default_name)) { name ->
            val project = CollectorProjectRegistry.createLocalProject(this, name)
            if (project == null || !CollectorProjectRegistry.activateProject(this, project.projectUid)) {
                Toast.makeText(this, R.string.project_create_failed, Toast.LENGTH_LONG).show()
                return@showNameDialog
            }
            (application as GISApplication).map?.save()
            openMap()
        }
    }

    private fun renameProject() {
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        if (!canMutateProject()) return
        showNameDialog(R.string.project_rename, project.name) { name ->
            val renamed = CollectorProjectRegistry.renameProject(this, project.projectUid, name)
            if (renamed == null) {
                Toast.makeText(this, R.string.project_rename_failed, Toast.LENGTH_LONG).show()
            } else {
                refresh()
            }
        }
    }

    private fun syncCurrentProject() {
        if (ProjectOperationCoordinator.isBusy()) {
            Toast.makeText(this, R.string.project_operation_wait, Toast.LENGTH_LONG).show()
            return
        }
        val started = OfflineSyncIntentService.startActionFoo(this)
        Toast.makeText(
            this,
            if (started) R.string.project_sync_started else R.string.project_operation_wait,
            if (started) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
        refresh()
    }

    private fun confirmDeleteProject() {
        val project = CollectorProjectRegistry.getActiveProject(this) ?: return
        if (!canMutateProject()) return
        val pending = collectLayerState().changedLayerCount
        val message = if (pending > 0) {
            getString(R.string.project_delete_confirmation_with_changes, project.name, pending)
        } else {
            getString(R.string.project_delete_confirmation, project.name)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.project_delete_local_copy)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.project_delete_confirm) { _, _ -> deleteProject(project) }
            .show()
    }

    private fun deleteProject(project: CollectorProjectRegistry.ProjectInfo) {
        setBusy(true)
        executor.execute {
            val result = CollectorProjectRegistry.deleteActiveProject(
                this,
                project.projectUid,
                getString(R.string.project_local_default_name)
            ) { backupChangedNgwLayers() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                if (result.isSuccess) {
                    Toast.makeText(this, R.string.project_delete_done, Toast.LENGTH_LONG).show()
                    openMap()
                } else {
                    val message = when (result.status) {
                        CollectorProjectRegistry.DeleteResult.Status.TRACKING ->
                            R.string.collector_project_switch_tracking
                        CollectorProjectRegistry.DeleteResult.Status.BUSY ->
                            R.string.project_operation_wait
                        CollectorProjectRegistry.DeleteResult.Status.BACKUP_FAILED ->
                            R.string.project_delete_backup_failed
                        else -> R.string.project_delete_failed
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }
    }

    private fun backupChangedNgwLayers(): Boolean {
        val app = application as GISApplication
        val layers = ArrayList<ILayer>()
        LayerGroup.getLayersByType(app.map, Constants.LAYERTYPE_NGW_VECTOR, layers)
        for (layer in layers.filterIsInstance<NGWVectorLayer>()) {
            if (FeatureChanges.isChanges(layer.changeTableName)
                && !app.backupEditableLayerData(layer, LayerBackupManager.REASON_PROJECT_DELETE)
            ) {
                HyperLog.w(Constants.TAG, "Project deletion blocked by layer backup gate")
                return false
            }
        }
        return true
    }

    private fun collectLayerState(): LayerState {
        val app = application as GISApplication
        val layers = ArrayList<ILayer>()
        LayerGroup.getLayersByType(app.map, Constants.LAYERTYPE_NGW_VECTOR, layers)
        return LayerState(
            layers.size,
            layers.filterIsInstance<NGWVectorLayer>()
                .count { FeatureChanges.isChanges(it.changeTableName) }
        )
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

    private fun scheduleAutomaticSync(project: CollectorProjectRegistry.ProjectInfo) {
        if (project.isLocal) return
        val app = application as GISApplication
        val account = app.getAccount(project.accountName) ?: return
        if (ContentResolver.getSyncAutomatically(account, app.authority)) {
            SyncAccountWorker.scheduleSoon(this, account.name, GISApplication.getAccountSyncTime(account, app))
        }
    }

    private fun showNameDialog(title: Int, initialValue: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initialValue)
            setSelection(text.length)
        }
        val horizontalPadding = (20 * resources.displayMetrics.density).toInt()
        val holder = LinearLayout(this).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(holder)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = getString(R.string.project_name_required)
                } else {
                    dialog.dismiss()
                    onSave(name)
                }
            }
        }
        dialog.show()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        actionButtons.forEach { it.isEnabled = !busy }
    }

    private fun openMap() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private data class LayerState(val layerCount: Int, val changedLayerCount: Int)
}
