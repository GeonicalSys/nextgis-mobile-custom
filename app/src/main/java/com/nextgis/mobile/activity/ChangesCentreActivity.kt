package com.nextgis.mobile.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.nextgis.maplib.util.NetworkUtil
import com.nextgis.maplib.util.ProjectRecoveryGuard
import com.nextgis.maplib.util.UploadOperationJournal
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.util.*
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Status comes from current queued identities and durable server acknowledgements. */
class ChangesCentreActivity : AppCompatActivity() {
    companion object { const val EXTRA_PROJECT_UID = "changes_project_uid" }
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var projects = emptyList<CollectorProjectRegistry.ProjectInfo>()
    private var selected = ""
    private var offset = 0L
    private var loading = false
    private var busy = false
    private var generation = 0
    private var page: LocalChangesRepository.Page? = null
    private var pollUntil = 0L
    private val actions = ArrayList<Pair<Button, Boolean>>()
    private lateinit var status: TextView
    private val poll = object : Runnable {
        override fun run() {
            if (!loading && !busy && (ProjectOperationCoordinator.isBusy() ||
                        android.os.SystemClock.elapsedRealtime() < pollUntil ||
                        page?.rows?.any { it.state == LocalChangesRepository.State.SENDING } == true)) refresh()
            updateEnabled()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_changes_centre)
        supportActionBar?.apply { setDisplayHomeAsUpEnabled(true); setTitle(R.string.changes_centre) }
        status = findViewById(R.id.changes_status)
        projects = CollectorProjectRegistry.listProjects(this)
        selected = state?.getString("project") ?: intent.getStringExtra(EXTRA_PROJECT_UID)
                ?: CollectorProjectRegistry.getActiveProject(this)?.projectUid.orEmpty()
        offset = state?.getLong("offset", 0L) ?: 0L
        val spinner = findViewById<Spinner>(R.id.changes_project)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, projects.map { it.name })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val index = projects.indexOfFirst { it.projectUid == selected }.coerceAtLeast(0)
        if (projects.isNotEmpty()) { selected = projects[index].projectUid; spinner.setSelection(index) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val uid = projects.getOrNull(position)?.projectUid ?: return
                if (uid != selected) { selected = uid; offset = 0; page = null; status.text = ""; refresh() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.changes_refresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.changes_send).setOnClickListener { requestDelivery(selected) }
        findViewById<Button>(R.id.changes_recovery).setOnClickListener { openRecovery() }
        findViewById<Button>(R.id.changes_release).setOnClickListener { releaseCopy() }
        findViewById<Button>(R.id.changes_previous).setOnClickListener { offset = (offset - 50).coerceAtLeast(0); refresh() }
        findViewById<Button>(R.id.changes_next).setOnClickListener { offset += 50; refresh() }
    }
    override fun onResume() { super.onResume(); refresh(); handler.removeCallbacks(poll); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() { generation++; handler.removeCallbacks(poll); io.shutdownNow(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("project", selected); state.putLong("offset", offset); super.onSaveInstanceState(state)
    }
    private fun activeWork() = TrackerService.isTrackerServiceRunning(this) || WalkSessionStore.load(this) != null ||
            (application as MainApplication).stakeoutSession.isActive
    private fun canReview(): Boolean {
        val allowed = !loading && !busy && !ProjectOperationCoordinator.isBusy() && !activeWork()
        if (!allowed) status.setText(R.string.changes_wait)
        return allowed
    }
    private fun updateEnabled() {
        if (isDestroyed) return
        val idle = !loading && !busy
        findViewById<Spinner>(R.id.changes_project).isEnabled = idle
        findViewById<Button>(R.id.changes_refresh).isEnabled = idle
        findViewById<Button>(R.id.changes_send).isEnabled = idle && page != null && page?.recoveryHeld != true && page?.deliveryBlocked != true
        findViewById<Button>(R.id.changes_release).isEnabled = idle && !ProjectOperationCoordinator.isBusy() && !activeWork()
        findViewById<Button>(R.id.changes_previous).isEnabled = idle && offset > 0
        findViewById<Button>(R.id.changes_next).isEnabled = idle && page?.hasMore() == true
        actions.forEach { (button, review) -> button.isEnabled = idle && (!review || (!ProjectOperationCoordinator.isBusy() && !activeWork())) }
        findViewById<ProgressBar>(R.id.changes_progress).visibility = if (loading || busy) View.VISIBLE else View.GONE
    }
    private fun refresh() {
        if (busy || loading || selected.isEmpty() || isDestroyed) return
        loading = true; updateEnabled(); val current = ++generation; val uid = selected; val start = offset
        io.execute {
            val result = runCatching { LocalChangesRepository.load(applicationContext, uid, 50, start) }
            runOnUiThread {
                if (isDestroyed || isFinishing || current != generation) return@runOnUiThread
                loading = false
                result.onSuccess { next ->
                    if (start > 0 && start >= next.total) { offset = ((next.total - 1).coerceAtLeast(0) / 50) * 50; refresh(); return@onSuccess }
                    page = next; render(next)
                }.onFailure {
                    page = null; findViewById<LinearLayout>(R.id.changes_rows).removeAllViews(); actions.clear()
                    status.setText(R.string.changes_load_failed)
                }
                updateEnabled()
            }
        }
    }
    private fun render(data: LocalChangesRepository.Page) {
        findViewById<TextView>(R.id.changes_project_name).text = data.project.name
        findViewById<TextView>(R.id.changes_summary).text = getString(R.string.changes_summary, data.queueCount, data.confirmedCount)
        val date = if (data.lastFullSync > 0) format(data.lastFullSync) else getString(R.string.changes_never)
        findViewById<TextView>(R.id.changes_full_sync).text = getString(R.string.changes_full_sync, date)
        findViewById<TextView>(R.id.changes_hold).apply {
            visibility = if (data.recoveryHeld || data.deliveryBlocked) View.VISIBLE else View.GONE
            setText(if (data.deliveryBlocked) R.string.changes_source_paused else R.string.changes_recovery_hold)
        }
        findViewById<View>(R.id.changes_release).visibility = if (data.recoveryHeld && !data.deliveryBlocked) View.VISIBLE else View.GONE
        val rows = findViewById<LinearLayout>(R.id.changes_rows); rows.removeAllViews(); actions.clear()
        if (data.rows.isEmpty()) text(rows, getString(R.string.changes_empty))
        data.rows.forEach { row ->
            val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(20), 0, dp(12)) }
            rows.addView(block, LinearLayout.LayoutParams(-1, -2))
            text(block, row.layerName)
            val objectName = if (row.attachmentId >= 0) getString(R.string.changes_attachment, row.attachmentId, row.featureId)
                else getString(R.string.changes_object, row.featureId)
            text(block, objectName + if (row.label.isNotEmpty()) " · ${row.label}" else "")
            if (row.attachmentName.isNotEmpty()) text(block, row.attachmentName)
            text(block, getString(when {
                row.operation.endsWith("create") -> R.string.changes_create
                row.operation.endsWith("delete") -> R.string.changes_delete
                else -> R.string.changes_update
            }) + " · " + getString(stateText(row.state)))
            reason(row)?.let { text(block, it) }
            if (row.remoteId >= 0) text(block, getString(R.string.changes_server_id, row.remoteId))
            if (row.state == LocalChangesRepository.State.CONFIRMED && row.updated > 0)
                text(block, getString(R.string.changes_received, format(row.updated)))
            when (row.action) {
                LocalChangesRepository.Action.SEND -> button(block, R.string.changes_send, false) { requestDelivery(row.projectUid) }
                LocalChangesRepository.Action.REVIEW -> button(block, R.string.changes_action_review, true) { prepareReview(row) }
                LocalChangesRepository.Action.FINISH -> button(block, R.string.changes_action_finish, true) { verify(row, row.identity, row.remoteId) }
                LocalChangesRepository.Action.RECOVERY -> button(block, R.string.project_recovery, false) { openRecovery() }
                LocalChangesRepository.Action.FORM -> button(block, R.string.changes_action_form, false) { continueForm(row) }
                else -> Unit
            }
        }
        findViewById<TextView>(R.id.changes_page).text = getString(R.string.changes_page,
            if (data.rows.isEmpty()) 0L else data.offset + 1, data.offset + data.rows.size, data.total)
        if (status.text.isEmpty()) status.setText(if (data.queueCount > 0 || data.reviewCount > 0) R.string.changes_pending else R.string.changes_no_pending)
    }
    private fun stateText(state: LocalChangesRepository.State) = when (state) {
        LocalChangesRepository.State.LOCAL -> R.string.changes_local
        LocalChangesRepository.State.QUEUED -> R.string.changes_queued
        LocalChangesRepository.State.SENDING -> R.string.changes_sending
        LocalChangesRepository.State.REVIEW -> R.string.changes_review
        LocalChangesRepository.State.CONFIRMED -> R.string.changes_confirmed
        LocalChangesRepository.State.MISSING -> R.string.changes_missing
        LocalChangesRepository.State.READ_ERROR -> R.string.changes_read_error
    }
    private fun reason(row: LocalChangesRepository.Row): String? = when {
        row.reason.startsWith("http_") -> getString(R.string.changes_reason_http, row.reason.substringAfter("http_"))
        row.reason == "finish_form" -> getString(R.string.changes_reason_form)
        row.reason == "local_only" -> getString(R.string.changes_reason_local)
        row.reason == "recovered_source" -> getString(R.string.changes_source_paused)
        row.reason == "send_disabled" -> getString(R.string.changes_reason_disabled)
        row.reason == "read_only_layer" -> getString(R.string.changes_reason_read_only)
        row.reason == "account_missing" -> getString(R.string.changes_reason_account)
        row.reason == "target_changed" || row.reason == "inconsistent_acknowledgement" -> getString(R.string.changes_reason_target)
        row.reason == "local_row_missing" || row.reason == "layer_missing" -> getString(R.string.changes_reason_missing)
        row.reason == "unbound_operation" -> getString(R.string.changes_reason_unbound)
        row.reason == "retired_operation" -> getString(R.string.changes_reason_retired)
        row.reason == "request_in_progress" -> getString(R.string.changes_reason_inflight)
        row.reason == "review_approved" -> getString(R.string.changes_reason_approved)
        row.state == LocalChangesRepository.State.READ_ERROR -> getString(R.string.changes_reason_read)
        row.remoteId >= 0 && row.state == LocalChangesRepository.State.REVIEW -> getString(R.string.changes_reason_accepted)
        row.state == LocalChangesRepository.State.REVIEW -> getString(R.string.changes_reason_uncertain)
        row.reason == "response_uncertain" || row.reason == "upload_preparation_failed" -> getString(R.string.changes_reason_network)
        else -> null
    }
    private fun text(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply { text = value; setPadding(0, dp(4), 0, dp(4)) }, LinearLayout.LayoutParams(-1, -2))
    }
    private fun button(parent: LinearLayout, caption: Int, review: Boolean, action: () -> Unit) {
        val button = Button(this).apply { setText(caption); isAllCaps = false; setOnClickListener { if (!loading && !busy) action() } }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2)); actions.add(button to review)
    }
    private fun requestDelivery(uid: String) {
        if (loading || busy) return
        val requested = runCatching { LocalChangesRepository.request(this, uid) }
            .getOrElse { status.setText(R.string.changes_load_failed); return }
        if (requested) {
            status.setText(R.string.changes_requested); pollUntil = android.os.SystemClock.elapsedRealtime() + 60000; refresh()
        }
    }
    private fun <T> reviewWork(action: () -> T, refreshAfter: Boolean = true, done: (T) -> Unit) {
        if (!canReview()) return
        busy = true; status.setText(R.string.changes_busy); updateEnabled()
        io.execute {
            val result = runCatching(action)
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                busy = false
                result.onSuccess(done).onFailure { error ->
                    status.setText(if (generateSequence(error) { it.cause }.any { it is LocalChangesRepository.SiblingAcknowledgementException })
                        R.string.changes_sibling_acknowledged else R.string.changes_review_changed)
                }
                if (refreshAfter) refresh()
                updateEnabled()
            }
        }
    }
    private fun prepareReview(row: LocalChangesRepository.Row) {
        reviewWork({ LocalChangesRepository.withLayer(this, row.projectUid, row.layerPath) { layer ->
            layer.prepareChangeReview(row.changeId, row.identity, row.featureId, row.attachmentId, row.operation)
        } }, false) { identity -> showReview(row, identity) }
    }
    private fun showReview(row: LocalChangesRepository.Row, identity: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = getString(R.string.changes_id_hint)
            val candidate = if (row.remoteId >= 0) row.remoteId else if (row.operation.endsWith("create")) -1
                else if (row.attachmentId >= 0) row.attachmentId else row.featureId
            if (candidate >= 0) setText(candidate.toString())
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        text(content, getString(R.string.changes_review_message)); content.addView(input, LinearLayout.LayoutParams(-1, -2))
        var actionSelected = false
        val dialog = FittedDialogs.compatBuilder(this).setTitle(R.string.changes_action_review).setView(content)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.changes_verify, null)
            .setNeutralButton(if (row.operation.endsWith("create")) R.string.changes_not_found else R.string.changes_review_retry) { _, _ ->
                actionSelected = true; allowRepeat(row, identity)
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim().toLongOrNull()
                if (id == null || id < 0) { input.error = getString(R.string.changes_id_invalid); return@setOnClickListener }
                if (canReview()) { actionSelected = true; dialog.dismiss(); verify(row, identity, id) }
            }
        }
        dialog.setOnDismissListener { if (!actionSelected) refresh() }; dialog.show()
    }
    private fun verify(row: LocalChangesRepository.Row, identity: String?, id: Long) {
        if (identity == null) { prepareReview(row); return }
        reviewWork({ LocalChangesRepository.withLayer(this, row.projectUid, row.layerPath) { layer ->
            layer.reconcileUpload(identity, id)
        } }) { matched -> status.setText(if (matched) R.string.changes_verified else R.string.changes_review_changed) }
    }
    private fun allowRepeat(row: LocalChangesRepository.Row, identity: String) {
        val creation = row.operation.endsWith("create")
        FittedDialogs.compatBuilder(this).setTitle(R.string.changes_action_review)
            .setMessage(if (creation) R.string.changes_repeat_warning else R.string.changes_retry_warning)
            .setNegativeButton(android.R.string.cancel) { _, _ -> refresh() }
            .setPositiveButton(if (creation) R.string.changes_allow_repeat else R.string.changes_review_retry) { _, _ ->
                reviewWork({ LocalChangesRepository.withLayer(this, row.projectUid, row.layerPath) { layer ->
                    layer.authorizeUploadRetry(identity)
                } }) { allowed ->
                    status.setText(if (allowed) R.string.changes_retry_allowed else R.string.changes_review_changed)
                    if (allowed && page?.recoveryHeld != true) LocalChangesRepository.request(this, row.projectUid)
                }
            }.show()
    }
    private fun releaseCopy() {
        if (!canReview()) return
        FittedDialogs.compatBuilder(this).setTitle(R.string.changes_release).setMessage(R.string.changes_release_warning)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok) { _, _ ->
                reviewWork({ LocalChangesRepository.releaseReviewedCopy(this, selected) }) { released ->
                    status.setText(if (released) R.string.changes_release_done else R.string.changes_review_changed)
                    if (released) LocalChangesRepository.request(this, selected)
                }
            }.show()
    }
    private fun continueForm(row: LocalChangesRepository.Row) {
        val current = CollectorProjectRegistry.getActiveProject(this)
        val draft = FeatureFormDraftStore.load(this)
        if (current?.projectUid == row.projectUid && draft != null && LayerUtil.isEditFormDraftRecoverable(this, draft))
            LayerUtil.showEditFormFromDraft(this, draft)
        else { status.setText(R.string.changes_form_unavailable); startActivity(Intent(this, ProjectSettingsActivity::class.java)) }
    }
    private fun openRecovery() { startActivity(Intent(this, ProjectRecoveryActivity::class.java)) }
    private fun format(timestamp: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
