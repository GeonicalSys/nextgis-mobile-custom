package com.nextgis.mobile.activity

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.nextgis.maplibui.util.CollectorProjectRegistry
import com.nextgis.mobile.R

/** Project picker whose rows intentionally contain only the user-facing project name. */
object ProjectChooserDialog {
    fun show(
        context: Context,
        projects: List<CollectorProjectRegistry.ProjectInfo>,
        onSelected: (CollectorProjectRegistry.ProjectInfo) -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(),
                (16 * density).toInt(), (8 * density).toInt())
        }
        val rows = arrayOfNulls<CheckedTextView>(projects.size)
        val selected = intArrayOf(projects.indexOfFirst { it.isActive(context) })
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.collector_project_switch)
            .setView(ScrollView(context).apply { addView(container) })
            .setPositiveButton(R.string.collector_project_open, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val selectable = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
        projects.forEachIndexed { index, project ->
            val row = CheckedTextView(context).apply {
                text = project.name
                textSize = 18f
                gravity = android.view.Gravity.CENTER_VERTICAL
                minHeight = (60 * density).toInt()
                setPadding((18 * density).toInt(), 0, (18 * density).toInt(), 0)
                setCheckMarkDrawable(android.R.drawable.btn_radio)
                isChecked = index == selected[0]
                setBackgroundResource(selectable.resourceId)
                setOnClickListener {
                    val previous = selected[0]
                    if (previous in rows.indices) {
                        rows[previous]?.isChecked = false
                    }
                    selected[0] = index
                    isChecked = true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                }
            }
            rows[index] = row
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            if (index < projects.lastIndex) {
                container.addView(View(context).apply {
                    setBackgroundColor(0x33000000)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * density).coerceAtLeast(1f).toInt()
                ))
            }
        }
        dialog.setOnShowListener {
            val open = dialog.getButton(AlertDialog.BUTTON_POSITIVE) ?: return@setOnShowListener
            open.isEnabled = selected[0] >= 0
            open.setOnClickListener {
                val index = selected[0]
                if (index !in projects.indices) {
                    return@setOnClickListener
                }
                dialog.dismiss()
                onSelected(projects[index])
            }
        }
        dialog.show()
    }
}
