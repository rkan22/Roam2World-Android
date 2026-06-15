package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
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
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.displayName
import im.angry.openeuicc.util.isEnabled
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.OpenEuiccContextMarker
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
    private var installMethod: String = "QR Code Install"
    private var loadingProfiles: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
        renderStatus()
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
        installNew.setOnClickListener { showInstallEsimSheet() }
        refreshProfiles.setOnClickListener { loadLiveProfiles(showToast = true) }
        openNative.setOnClickListener { openNativeOpenEuicc() }
        openR2wHome.setOnClickListener {
            startActivity(Intent(this, R2wComposeHomeActivity::class.java))
        }
    }

    private fun renderStatus() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        nativeStatus.text = if (target.isNullOrBlank()) {
            "OpenEUICC unavailable"
        } else {
            "Ready • OpenEUICC"
        }
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
                            subtitle = "Refresh again or open native OpenEUICC."
                        )
                        Toast.makeText(
                            this@OpenEuiccIntegrationActivity,
                            "Profile loading failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> renderEmptyState()
                }

                if (showToast && loadResult.failures.isEmpty()) {
                    Toast.makeText(
                        this@OpenEuiccIntegrationActivity,
                        "Profiles refreshed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure {
                renderProfileState(
                    title = "Unable to load profiles",
                    subtitle = "Refresh again or open native OpenEUICC."
                )
                Toast.makeText(
                    this@OpenEuiccIntegrationActivity,
                    "Profile loading failed",
                    Toast.LENGTH_SHORT
                ).show()
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
                        val channelProfiles = if (showUnfilteredProfiles) {
                            channel.lpa.profiles
                        } else {
                            channel.lpa.profiles.operational
                        }
                        channelProfiles.map { it.toProfileUi(channel, seId) }
                    }
                }.onSuccess {
                    loadedProfiles.addAll(it)
                }.onFailure {
                    failures.add(it)
                }
            }
        }

        return ProfileLoadResult(
            profiles = loadedProfiles,
            successfulChannels = successfulChannels,
            failures = failures
        )
    }

    private fun renderProfiles(liveProfiles: List<ProfileUi>) {
        profiles.removeAllViews()
        liveProfiles.forEach { profile ->
            profiles.addView(profileView(profile))
        }
    }

    private fun renderLoadingState() {
        renderProfileState(
            title = "Loading profiles",
            subtitle = "Reading live eUICC profile data..."
        )
    }

    private fun renderEmptyState() {
        renderProfileState(
            title = "No profiles found",
            subtitle = "Connect an eUICC reader or refresh profiles."
        )
    }

    private fun renderProfileState(title: String, subtitle: String) {
        profiles.removeAllViews()
        profiles.addView(cleanCard().apply {
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(14), dp(14), dp(14), dp(14))

                addView(
                    iconBadge(R.drawable.r2w_ic_sim_clean, R.drawable.r2w_clean_icon_circle),
                    LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                        rightMargin = dp(12)
                    }
                )

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumWidth = 0

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
                        maxLines = 2
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
        val view = LayoutInflater.from(this).inflate(R.layout.openeuicc_profile_item, profiles, false)
        view.requireViewById<TextView>(R.id.openeuicc_profile_name).text = profile.name
        view.requireViewById<TextView>(R.id.openeuicc_profile_iccid).text = shortIccid(profile.iccid)
        view.requireViewById<TextView>(R.id.openeuicc_profile_date).text = "Installed • ${profile.date}"
        view.requireViewById<TextView>(R.id.openeuicc_profile_provider).text = "Provider • ${profile.provider}"
        view.requireViewById<TextView>(R.id.openeuicc_profile_status).apply {
            text = profile.status
            setTextColor(getColor(statusColor(profile.status)))
            setBackgroundResource(statusBackground(profile.status))
        }
        view.setOnClickListener { showProfileDetails(profile) }
        view.requireViewById<View>(R.id.openeuicc_profile_switch).setOnClickListener {
            Toast.makeText(this, "Switch profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }
        view.requireViewById<View>(R.id.openeuicc_profile_delete).setOnClickListener {
            Toast.makeText(this, "Delete profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }
        return view
    }

    private fun showInstallEsimSheet() {
        val dialog = BottomSheetDialog(this)
        val root = sheetRoot()

        root.addView(sheetHandle())
        root.addView(
            sheetHeader(
                title = "Install New eSIM",
                subtitle = "QR, activation code, or manual install",
                iconRes = R.drawable.r2w_ic_qr_clean,
                iconBackground = R.drawable.r2w_clean_orange_circle,
                chipText = "OpenEUICC"
            )
        )

        val methodRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
        }

        val methods = listOf("QR", "Code", "Manual")
        val buttons = mutableListOf<MaterialButton>()

        fun updateButtons() {
            buttons.forEach { button ->
                val selected = when (button.text.toString()) {
                    "QR" -> installMethod.startsWith("QR")
                    "Code" -> installMethod.startsWith("Activation")
                    else -> installMethod.startsWith("Manual")
                }
                button.backgroundTintList = ColorStateList.valueOf(
                    getColor(if (selected) R.color.r2w_clean_primary else R.color.r2w_clean_surface)
                )
                button.setStrokeColor(
                    ColorStateList.valueOf(
                        getColor(if (selected) R.color.r2w_clean_primary else R.color.r2w_clean_border)
                    )
                )
                button.setTextColor(getColor(if (selected) android.R.color.white else R.color.r2w_clean_primary))
            }
        }

        methods.forEachIndexed { index, method ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = method
                isAllCaps = false
                cornerRadius = dp(16)
                minHeight = dp(42)
                insetTop = 0
                insetBottom = 0
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener {
                    installMethod = when (method) {
                        "QR" -> "QR Code Install"
                        "Code" -> "Activation Code Install"
                        else -> "Manual Install"
                    }
                    updateButtons()
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    dp(44),
                    1f
                ).apply {
                    if (index > 0) leftMargin = dp(8)
                }
            }
            buttons.add(button)
            methodRow.addView(button)
        }

        root.addView(methodRow)
        updateButtons()

        val card = cleanCard(topMargin = 14)

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val qrPlaceholder = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.r2w_install_qr_placeholder)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(124)
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.r2w_ic_qr_clean)
                    contentDescription = null
                },
                FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER)
            )
        }

        cardContent.addView(qrPlaceholder)

        cardContent.addView(TextView(this).apply {
            text = "Ready to install"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_clean_text))
            setPadding(0, dp(14), 0, 0)
        })

        cardContent.addView(TextView(this).apply {
            text = "OpenEUICC will handle scanning, activation code entry and profile download."
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(getColor(R.color.r2w_clean_muted))
            setPadding(0, dp(6), 0, 0)
        })

        card.addView(cardContent)
        root.addView(card)

        root.addView(actionButton("Continue with OpenEUICC", false, R.drawable.r2w_ic_download_clean) {
            dialog.dismiss()
            openNativeOpenEuicc()
        })

        root.addView(actionButton("Cancel", true) {
            dialog.dismiss()
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showProfileDetails(profile: ProfileUi) {
        val dialog = BottomSheetDialog(this)
        val root = sheetRoot()

        root.addView(sheetHandle())
        root.addView(
            sheetHeader(
                title = profile.name,
                subtitle = "${profile.provider} • ${profile.slot}",
                iconRes = R.drawable.r2w_ic_sim_clean,
                chipText = profile.status,
                chipBackground = statusBackground(profile.status),
                chipColor = statusColor(profile.status)
            )
        )

        val infoCard = cleanCard(topMargin = 10)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        info.addView(TextView(this).apply {
            text = "Profile Information"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.r2w_clean_text))
        })

        info.addView(detailRow("Profile Name", profile.name))
        info.addView(detailRow("ICCID", profile.iccid.removePrefix("ICCID ").trim()))
        info.addView(detailRow("Provider", profile.provider))
        info.addView(detailRow("Status", profile.status, chip = true))
        info.addView(detailRow("Slot", profile.slot))
        info.addView(detailRow("Install Date", profile.date))
        info.addView(detailRow("Last Updated", profile.date))
        info.addView(detailRow("Profile Class", profile.profileClass))

        infoCard.addView(info)
        root.addView(infoCard)

        root.addView(actionButton("Enable Profile", false, R.drawable.r2w_ic_sim_clean) {
            dialog.dismiss()
            Toast.makeText(this, "Enable profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        })

        val secondaryActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        secondaryActions.addView(actionButton("Disable", true, R.drawable.r2w_ic_shield_clean) {
            dialog.dismiss()
            Toast.makeText(this, "Disable profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                rightMargin = dp(6)
            }
        })

        secondaryActions.addView(actionButton("Delete", true, R.drawable.r2w_ic_trash_clean, danger = true) {
            dialog.dismiss()
            Toast.makeText(this, "Delete profile via native OpenEUICC", Toast.LENGTH_SHORT).show()
            openNativeOpenEuicc()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                leftMargin = dp(6)
            }
        })

        root.addView(secondaryActions)

        root.addView(actionButton("Refresh Status", true, R.drawable.r2w_ic_refresh_clean) {
            dialog.dismiss()
            Toast.makeText(this, "Profile status refreshed", Toast.LENGTH_SHORT).show()
            renderStatus()
            loadLiveProfiles()
        })

        root.addView(TextView(this).apply {
            text = "Advanced profile actions are executed through the native OpenEUICC engine. Make sure you know which profile is active before disabling or deleting it."
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(getColor(R.color.r2w_clean_muted))
            setBackgroundResource(R.drawable.r2w_profile_details_warning_box)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun sheetRoot(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
            setBackgroundColor(getColor(R.color.r2w_clean_bg))
        }

    private fun sheetHandle(): View =
        View(this).apply {
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

        row.addView(
            iconBadge(iconRes, iconBackground),
            LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                rightMargin = dp(12)
            }
        )

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = 0
        }

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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(7)
            }
        })

        row.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        return card
    }

    private fun iconBadge(iconRes: Int, backgroundRes: Int): FrameLayout =
        FrameLayout(this).apply {
            setBackgroundResource(backgroundRes)
            addView(
                ImageView(context).apply {
                    setImageResource(iconRes)
                    contentDescription = null
                },
                FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
            )
        }

    private fun cleanCard(topMargin: Int = 0): MaterialCardView =
        MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(getColor(R.color.r2w_clean_border))
            setCardBackgroundColor(getColor(R.color.r2w_clean_surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (topMargin > 0) this.topMargin = dp(topMargin)
            }
        }

    private fun detailRow(label: String, value: String, chip: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(7))

            addView(TextView(context).apply {
                text = label
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(getColor(R.color.r2w_clean_muted))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(TextView(context).apply {
                text = value
                gravity = Gravity.END
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(if (chip) statusColor(value) else R.color.r2w_clean_text))
                if (chip) {
                    setBackgroundResource(statusBackground(value))
                    setPadding(dp(12), dp(5), dp(12), dp(5))
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f))
        }

    private fun actionButton(
        textValue: String,
        outlined: Boolean,
        iconRes: Int? = null,
        danger: Boolean = false,
        action: () -> Unit
    ): MaterialButton =
        MaterialButton(
            this,
            null,
            if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle
        ).apply {
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
                iconTint = ColorStateList.valueOf(
                    getColor(
                        when {
                            !outlined -> android.R.color.white
                            danger -> R.color.r2w_clean_error
                            else -> R.color.r2w_clean_primary
                        }
                    )
                )
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(8)
            }
        }

    private fun shortIccid(iccid: String): String {
        val digits = iccid.removePrefix("ICCID").trim()
        return if (digits.length > 10) {
            "ICCID • ${digits.take(6)}...${digits.takeLast(4)}"
        } else {
            "ICCID • $digits"
        }
    }

    private fun statusBackground(status: String): Int =
        when (status.lowercase()) {
            "active" -> R.drawable.r2w_clean_connected_chip
            "pending" -> R.drawable.r2w_clean_warning_chip
            else -> R.drawable.r2w_clean_inactive_chip
        }

    private fun statusColor(status: String): Int =
        when (status.lowercase()) {
            "active" -> R.color.r2w_clean_success
            "pending" -> R.color.r2w_clean_warning
            else -> R.color.r2w_clean_muted
        }

    private fun LocalProfileInfo.toProfileUi(
        channel: EuiccChannel,
        seId: EuiccChannel.SecureElementId
    ): ProfileUi =
        ProfileUi(
            name = displayName.ifBlank { "--" },
            iccid = iccid,
            status = if (isEnabled) "Active" else "Disabled",
            date = "--",
            provider = providerName.ifBlank { "--" },
            slot = channel.slotLabel(seId),
            profileClass = profileClass.label
        )

    private fun EuiccChannel.slotLabel(seId: EuiccChannel.SecureElementId): String {
        val base = if (slotId == EuiccChannelManager.USB_CHANNEL_ID) {
            "USB reader"
        } else {
            "Slot $logicalSlotId • Port $portId"
        }
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

    private fun targetActivityName(key: String): String? =
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString(key)

    private data class ProfileUi(
        val name: String,
        val iccid: String,
        val status: String,
        val date: String,
        val provider: String,
        val slot: String,
        val profileClass: String
    )

    private data class ProfileLoadResult(
        val profiles: List<ProfileUi>,
        val successfulChannels: Int,
        val failures: List<Throwable>
    )
}
