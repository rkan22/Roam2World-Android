package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.core.EuiccChannelManager
import im.angry.openeuicc.util.OpenEuiccContextMarker
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.displayName
import im.angry.openeuicc.util.isEnabled
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.operational
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.LocalProfileInfo
import net.typeblog.lpac_jni.ProfileClass

class OpenEuiccIntegrationActivity : BaseEuiccAccessActivity(), OpenEuiccContextMarker {
    private lateinit var scroll: NestedScrollView
    private lateinit var nativeStatus: TextView
    private lateinit var profiles: LinearLayout
    private lateinit var installNew: MaterialButton
    private lateinit var refreshProfiles: MaterialButton
    private lateinit var openNative: MaterialButton
    private lateinit var openR2wHome: MaterialButton
    private var loadingProfiles: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        R2wUsbPermissionHelper.requestAttachedUsbReaders(this, showToast = true)
        setContentView(R.layout.activity_openeuicc_integration)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "OpenEUICC"

        scroll = requireViewById(R.id.openeuicc_scroll)
        nativeStatus = requireViewById(R.id.openeuicc_native_status)
        profiles = requireViewById(R.id.openeuicc_profiles)
        installNew = requireViewById(R.id.openeuicc_install_new)
        refreshProfiles = requireViewById(R.id.openeuicc_refresh_profiles)
        openNative = requireViewById(R.id.openeuicc_open_native)
        openR2wHome = requireViewById(R.id.openeuicc_open_r2w_home)

        setupInsets()
        renderStatus("Ready • OpenEUICC")
        renderLoadingState()
        setupActions()

        if (euiccChannelManagerLoaded.isCompleted) {
            loadLiveProfiles()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onInit() {
        if (::profiles.isInitialized) {
            loadLiveProfiles()
        }
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun setupActions() {
        installNew.setOnClickListener { openNativeOpenEuicc() }
        refreshProfiles.setOnClickListener { loadLiveProfiles(showToast = true) }
        openNative.setOnClickListener { openNativeOpenEuicc() }
        openR2wHome.setOnClickListener { startActivity(Intent(this, R2wComposeHomeActivity::class.java)) }
    }

    private fun renderStatus(text: String? = null) {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        nativeStatus.text = text ?: if (target.isNullOrBlank()) "OpenEUICC unavailable" else "Ready • OpenEUICC"
    }

    private fun loadLiveProfiles(showToast: Boolean = false) {
        if (loadingProfiles) return
        loadingProfiles = true
        refreshProfiles.isEnabled = false
        renderLoadingState()

        lifecycleScope.launch {
            val result = runCatching { loadProfilesFromOpenEuicc() }
            loadingProfiles = false
            refreshProfiles.isEnabled = true
            renderStatus()

            result.onSuccess { loadResult ->
                when {
                    loadResult.profiles.isNotEmpty() -> renderProfiles(loadResult.profiles)
                    loadResult.failures.isNotEmpty() && loadResult.successfulChannels == 0 -> {
                        renderProfileState(
                            title = "Unable to load profiles",
                            subtitle = "USB reader was found, but no valid eUICC channel could be read."
                        )
                        Toast.makeText(this@OpenEuiccIntegrationActivity, "Profile loading failed", Toast.LENGTH_SHORT).show()
                    }
                    else -> renderEmptyState()
                }
                if (showToast && loadResult.failures.isEmpty()) {
                    Toast.makeText(this@OpenEuiccIntegrationActivity, "Profiles refreshed", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                renderProfileState(
                    title = "Unable to load profiles",
                    subtitle = it.message ?: "Refresh again or reconnect the USB reader."
                )
                Toast.makeText(this@OpenEuiccIntegrationActivity, "Profile loading failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadProfilesFromOpenEuicc(): ProfileLoadResult {
        euiccChannelManagerLoaded.await()
        euiccChannelManagerService.waitForForegroundTask()

        val showUnfilteredProfiles = preferenceRepository.unfilteredProfileListFlow.first()
        val loadedProfiles = mutableListOf<ProfileUi>()
        val failures = mutableListOf<Throwable>()
        var successfulChannels = 0

        val ports = euiccChannelManager.flowAllOpenEuiccPorts().toList()
        ports.forEach { (slotId, portId) ->
            val seIds = euiccChannelManager.flowEuiccSecureElements(slotId, portId).toList()
            seIds.forEach { seId ->
                runCatching {
                    euiccChannelManager.withEuiccChannel(slotId, portId, seId) { channel ->
                        successfulChannels += 1
                        euiccChannelManager.notifyEuiccProfilesChanged(channel.logicalSlotId)
                        val channelProfiles = if (showUnfilteredProfiles) channel.lpa.profiles else channel.lpa.profiles.operational
                        channelProfiles.map { it.toProfileUi(channel, seId) }
                    }
                }.onSuccess {
                    loadedProfiles.addAll(it)
                }.onFailure {
                    failures.add(it)
                }
            }
        }

        return ProfileLoadResult(loadedProfiles, successfulChannels, failures)
    }

    private fun renderProfiles(liveProfiles: List<ProfileUi>) {
        profiles.removeAllViews()
        liveProfiles.forEach { profile -> profiles.addView(profileView(profile)) }
    }

    private fun renderLoadingState() = renderProfileState(
        title = "Loading profiles",
        subtitle = "Reading live eUICC profile data..."
    )

    private fun renderEmptyState() = renderProfileState(
        title = "No profiles found",
        subtitle = "Connect an eUICC reader or refresh profiles."
    )

    private fun renderProfileState(title: String, subtitle: String) {
        profiles.removeAllViews()
        profiles.addView(cleanCard().apply {
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                addView(iconBadge(R.drawable.r2w_ic_sim_clean, R.drawable.r2w_clean_icon_circle), LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = title
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(getColor(R.color.r2w_clean_text))
                    })
                    addView(TextView(context).apply {
                        text = subtitle
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                        setTextColor(getColor(R.color.r2w_clean_muted))
                        setPadding(0, dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
        })
    }

    private fun profileView(profile: ProfileUi): View {
        val card = cleanCard(topMargin = 10)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        root.addView(TextView(this).apply {
            text = profile.name
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_clean_text))
        })
        root.addView(TextView(this).apply {
            text = "${shortIccid(profile.iccid)} • ${profile.provider} • ${profile.slot}"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.r2w_clean_muted))
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = profile.status
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(statusColor(profile.status)))
            setBackgroundResource(statusBackground(profile.status))
            setPadding(dp(12), dp(5), dp(12), dp(5))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        }
        actionRow.addView(actionButton(if (profile.enabled) "Disable" else "Enable", false, R.drawable.r2w_ic_sim_clean) {
            setProfileEnabled(profile, !profile.enabled)
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) } })
        actionRow.addView(actionButton("Delete", true, R.drawable.r2w_ic_trash_clean, danger = true) {
            deleteProfile(profile)
        }.apply { layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) } })
        root.addView(actionRow)

        card.addView(root)
        card.setOnClickListener { showProfileDetails(profile) }
        return card
    }

    private fun setProfileEnabled(profile: ProfileUi, enable: Boolean) {
        lifecycleScope.launch {
            loadingProfiles = true
            renderStatus(if (enable) "Enabling profile..." else "Disabling profile...")
            val result = runCatching {
                euiccChannelManagerLoaded.await()
                euiccChannelManagerService.waitForForegroundTask()
                euiccChannelManager.withEuiccChannel(profile.slotId, profile.portId, profile.seId) { channel ->
                    if (enable) channel.lpa.enableProfile(profile.iccid, true) else channel.lpa.disableProfile(profile.iccid, true)
                    euiccChannelManager.notifyEuiccProfilesChanged(channel.logicalSlotId)
                }
            }
            loadingProfiles = false
            result.onSuccess {
                Toast.makeText(this@OpenEuiccIntegrationActivity, if (enable) "Profile enabled" else "Profile disabled", Toast.LENGTH_SHORT).show()
                loadLiveProfiles(showToast = false)
            }.onFailure {
                renderStatus("Profile action failed")
                Toast.makeText(this@OpenEuiccIntegrationActivity, it.message ?: "Profile action failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteProfile(profile: ProfileUi) {
        lifecycleScope.launch {
            loadingProfiles = true
            renderStatus("Deleting profile...")
            val result = runCatching {
                euiccChannelManagerLoaded.await()
                euiccChannelManagerService.waitForForegroundTask()
                euiccChannelManager.withEuiccChannel(profile.slotId, profile.portId, profile.seId) { channel ->
                    channel.lpa.deleteProfile(profile.iccid)
                    euiccChannelManager.notifyEuiccProfilesChanged(channel.logicalSlotId)
                }
            }
            loadingProfiles = false
            result.onSuccess {
                Toast.makeText(this@OpenEuiccIntegrationActivity, "Profile deleted", Toast.LENGTH_SHORT).show()
                loadLiveProfiles(showToast = false)
            }.onFailure {
                renderStatus("Delete failed")
                Toast.makeText(this@OpenEuiccIntegrationActivity, it.message ?: "Delete failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showProfileDetails(profile: ProfileUi) {
        val dialog = BottomSheetDialog(this)
        val root = sheetRoot()
        root.addView(sheetHandle())
        root.addView(sheetHeader(profile.name, "${profile.provider} • ${profile.slot}", R.drawable.r2w_ic_sim_clean, chipText = profile.status, chipBackground = statusBackground(profile.status), chipColor = statusColor(profile.status)))
        root.addView(actionButton(if (profile.enabled) "Disable Profile" else "Enable Profile", false, R.drawable.r2w_ic_sim_clean) {
            dialog.dismiss()
            setProfileEnabled(profile, !profile.enabled)
        })
        root.addView(actionButton("Delete", true, R.drawable.r2w_ic_trash_clean, danger = true) {
            dialog.dismiss()
            deleteProfile(profile)
        })
        root.addView(actionButton("Refresh Status", true, R.drawable.r2w_ic_refresh_clean) {
            dialog.dismiss()
            loadLiveProfiles(showToast = true)
        })
        dialog.setContentView(root)
        dialog.show()
    }

    private fun sheetRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(14))
        setBackgroundColor(getColor(R.color.r2w_clean_bg))
    }

    private fun sheetHandle(): View = View(this).apply {
        setBackgroundResource(R.drawable.r2w_sheet_handle)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(10)
        }
    }

    private fun sheetHeader(
        title: String,
        subtitle: String,
        iconRes: Int,
        iconBackground: Int = R.drawable.r2w_clean_icon_circle,
        chipText: String,
        chipBackground: Int = R.drawable.r2w_clean_connected_chip,
        chipColor: Int = R.color.r2w_clean_success
    ): View {
        val card = cleanCard()
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        row.addView(iconBadge(iconRes, iconBackground), LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(TextView(this).apply {
            text = title
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_clean_text))
        })
        copy.addView(TextView(this).apply {
            text = subtitle
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.r2w_clean_muted))
            setPadding(0, dp(3), 0, 0)
        })
        copy.addView(TextView(this).apply {
            text = chipText
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(chipColor))
            setBackgroundResource(chipBackground)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) }
        })
        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun iconBadge(iconRes: Int, backgroundRes: Int): FrameLayout = FrameLayout(this).apply {
        setBackgroundResource(backgroundRes)
        addView(ImageView(context).apply { setImageResource(iconRes); contentDescription = null }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
    }

    private fun cleanCard(topMargin: Int = 0): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        setStrokeColor(getColor(R.color.r2w_clean_border))
        setCardBackgroundColor(getColor(R.color.r2w_clean_surface))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (topMargin > 0) this.topMargin = dp(topMargin) }
    }

    private fun actionButton(
        textValue: String,
        outlined: Boolean,
        iconRes: Int? = null,
        danger: Boolean = false,
        action: () -> Unit
    ): MaterialButton = MaterialButton(this, null, if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        text = textValue
        isAllCaps = false
        cornerRadius = dp(16)
        insetTop = 0
        insetBottom = 0
        textSize = 14f
        if (outlined) {
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.r2w_clean_surface))
            setStrokeColor(ColorStateList.valueOf(getColor(R.color.r2w_clean_border)))
            setTextColor(getColor(if (danger) R.color.r2w_clean_error else R.color.r2w_clean_primary))
        } else {
            backgroundTintList = null
            setBackgroundResource(R.drawable.r2w_clean_primary_button)
            setTextColor(getColor(android.R.color.white))
        }
        iconRes?.let {
            setIconResource(it)
            iconTint = ColorStateList.valueOf(getColor(if (!outlined) android.R.color.white else if (danger) R.color.r2w_clean_error else R.color.r2w_clean_primary))
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) }
    }

    private fun shortIccid(iccid: String): String {
        val digits = iccid.removePrefix("ICCID").trim()
        return if (digits.length > 10) "ICCID • ${digits.take(6)}...${digits.takeLast(4)}" else "ICCID • $digits"
    }

    private fun statusBackground(status: String): Int = when (status.lowercase()) {
        "active" -> R.drawable.r2w_clean_connected_chip
        "pending" -> R.drawable.r2w_clean_warning_chip
        else -> R.drawable.r2w_clean_inactive_chip
    }

    private fun statusColor(status: String): Int = when (status.lowercase()) {
        "active" -> R.color.r2w_clean_success
        "pending" -> R.color.r2w_clean_warning
        else -> R.color.r2w_clean_muted
    }

    private fun LocalProfileInfo.toProfileUi(channel: EuiccChannel, seId: EuiccChannel.SecureElementId): ProfileUi =
        ProfileUi(
            name = displayName.ifBlank { "--" },
            iccid = iccid,
            status = if (isEnabled) "Active" else "Disabled",
            enabled = isEnabled,
            date = "--",
            provider = providerName.ifBlank { "--" },
            slot = channel.slotLabel(seId),
            profileClass = profileClass.label,
            slotId = channel.slotId,
            portId = channel.portId,
            seId = seId
        )

    private fun EuiccChannel.slotLabel(seId: EuiccChannel.SecureElementId): String {
        val base = if (slotId == EuiccChannelManager.USB_CHANNEL_ID) "USB reader" else "Slot $logicalSlotId • Port $portId"
        return if (hasMultipleSE) "$base • SE ${seId.id}" else base
    }

    private val ProfileClass.label: String
        get() = when (this) {
            ProfileClass.Testing -> "Testing"
            ProfileClass.Provisioning -> "Provisioning"
            ProfileClass.Operational -> "Operational"
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun openNativeOpenEuicc() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            Toast.makeText(this, "OpenEUICC target unavailable", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent().setClassName(this, target))
    }

    private fun targetActivityName(key: String): String? = packageManager
        .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString(key)

    private data class ProfileUi(
        val name: String,
        val iccid: String,
        val status: String,
        val enabled: Boolean,
        val date: String,
        val provider: String,
        val slot: String,
        val profileClass: String,
        val slotId: Int,
        val portId: Int,
        val seId: EuiccChannel.SecureElementId
    )

    private data class ProfileLoadResult(
        val profiles: List<ProfileUi>,
        val successfulChannels: Int,
        val failures: List<Throwable>
    )
}
