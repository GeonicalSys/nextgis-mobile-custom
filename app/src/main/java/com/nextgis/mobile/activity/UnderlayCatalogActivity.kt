package com.nextgis.mobile.activity

import android.os.Bundle
import android.view.View
import android.widget.*
import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.SharedUnderlayCatalog
import com.nextgis.maplib.util.SharedUnderlayStore
import com.nextgis.maplibui.util.ProjectOperationCoordinator
import com.nextgis.maplibui.util.SharedUnderlayProjects
import com.nextgis.mobile.R
import java.util.concurrent.Executors

class UnderlayCatalogActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var progress: ProgressBar
    private var assets = emptyList<SharedUnderlayCatalog.Asset>()
    private val choose get() = intent.getBooleanExtra("choose", false)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = getString(if (choose) R.string.underlay_from_catalog else R.string.underlay_catalog)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        progress = ProgressBar(this)
        list = ListView(this)
        val empty = TextView(this).apply { setText(R.string.underlay_catalog_empty); setPadding(24, 24, 24, 24) }
        layout.addView(progress); layout.addView(empty); layout.addView(list)
        list.emptyView = empty
        setContentView(layout)
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
        work { SharedUnderlayProjects.prepare(this); refresh() }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { executor.shutdown(); super.onDestroy() }

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
    private fun work(action: () -> Unit) {
        val lease = ProjectOperationCoordinator.tryBegin(this, ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION)
        if (lease == null) { Toast.makeText(this, R.string.project_operation_wait, Toast.LENGTH_LONG).show(); return }
        list.isEnabled = false; progress.visibility = View.VISIBLE
        executor.execute {
            try { action() }
            catch (error: Exception) {
                HyperLog.w(Constants.TAG, "Shared underlay operation failed", error)
                runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this, R.string.underlay_operation_failed, Toast.LENGTH_LONG).show() }
            } finally {
                lease.close()
                runOnUiThread { if (!isDestroyed) { list.isEnabled = true; progress.visibility = View.GONE } }
            }
        }
    }
}
