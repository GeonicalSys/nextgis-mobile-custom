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
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.collector_project_switch)
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        projects.forEachIndexed { index, project ->
            val row = CheckedTextView(context).apply {
                text = project.name
                textSize = 18f
                gravity = android.view.Gravity.CENTER_VERTICAL
                minHeight = (60 * density).toInt()
                setPadding((18 * density).toInt(), 0, (18 * density).toInt(), 0)
                setCheckMarkDrawable(android.R.drawable.btn_radio)
                isChecked = project.isActive(context)
                val outValue = TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    dialog.dismiss()
                    onSelected(project)
                }
            }
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
        dialog.show()
    }
}
