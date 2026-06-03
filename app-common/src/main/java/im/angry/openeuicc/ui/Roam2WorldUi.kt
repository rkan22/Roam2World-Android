package im.angry.openeuicc.ui

import android.content.res.ColorStateList
import android.widget.TextView
import com.google.android.material.color.MaterialColors

fun TextView.applyRoamStatusChip(label: String?, rawStatus: String? = label) {
    if (label.isNullOrBlank()) {
        text = ""
        visibility = android.view.View.GONE
        return
    }

    text = label
    visibility = android.view.View.VISIBLE
    val normalized = rawStatus.orEmpty().trim().lowercase()
    val (containerAttr, onContainerAttr) = when (normalized) {
        "active", "approved", "complete", "completed", "confirmed" ->
            com.google.android.material.R.attr.colorPrimaryContainer to
                com.google.android.material.R.attr.colorOnPrimaryContainer

        "failed", "failure", "rejected", "cancelled", "canceled", "suspended" ->
            com.google.android.material.R.attr.colorErrorContainer to
                com.google.android.material.R.attr.colorOnErrorContainer

        "refunded", "refund", "pending_provider_balance" ->
            com.google.android.material.R.attr.colorTertiaryContainer to
                com.google.android.material.R.attr.colorOnTertiaryContainer

        else ->
            com.google.android.material.R.attr.colorSecondaryContainer to
                com.google.android.material.R.attr.colorOnSecondaryContainer
    }
    backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, containerAttr))
    setTextColor(MaterialColors.getColor(this, onContainerAttr))
}

fun TextView.applyRoamProviderChip(provider: String?) {
    if (provider.isNullOrBlank()) {
        text = ""
        visibility = android.view.View.GONE
        return
    }

    text = provider
    visibility = android.view.View.VISIBLE
    backgroundTintList = ColorStateList.valueOf(
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer)
    )
    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer))
}
