package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileEsim
import im.angry.openeuicc.auth.MobileEsimFilters
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MobileEsimsActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var esims: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView
    private lateinit var search: TextInputEditText

    private var allEsims: List<MobileEsim> = emptyList()
    private var selectedFilter: EsimFilter = EsimFilter.ALL
    private var initialFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_esims)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.mobile_esims_title)

        refresh = requireViewById(R.id.mobile_esims_refresh)
        bottomNav = requireViewById(R.id.mobile_esims_bottom_nav)
        esims = requireViewById(R.id.mobile_esims_list)
        empty = requireViewById(R.id.mobile_esims_empty)
        error = requireViewById(R.id.mobile_esims_error)
        search = requireViewById(R.id.mobile_esims_search)
        initialFilter = intent.getStringExtra(MobileEsimFilters.FILTER_EXTRA_KEY)?.trim()

        setupInsets()
        setupBottomNavigation()
        setupTabs()
        setupSearch()
        refresh.setOnRefreshListener { loadEsims() }
        renderEsims(emptyList())
        loadEsims()
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_esims
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_mobile_esims, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.reload -> {
                loadEsims()
                true
            }

            R.id.open_device_esim_manager -> {
                openNativeEsimActivity()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(refresh),
                { insets ->
                    bottomNav.updatePadding(
                        insets.left,
                        bottomNav.paddingTop,
                        insets.right,
                        insets.bottom
                    )
                }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    openDashboardActivity()
                    false
                }
                R.id.nav_packages -> {
                    openPackagesActivity()
                    false
                }
                R.id.nav_wallet -> {
                    openWalletActivity()
                    false
                }
                R.id.nav_esims -> true
                R.id.nav_more -> {
                    openMoreActivity()
                    false
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_esims
    }

    private fun setupTabs() {
        requireViewById<Chip>(R.id.mobile_esims_tab_all).setOnClickListener {
            selectedFilter = EsimFilter.ALL
            initialFilter = null
            applyFilters()
        }
        requireViewById<Chip>(R.id.mobile_esims_tab_active).setOnClickListener {
            selectedFilter = EsimFilter.ACTIVE
            initialFilter = null
            applyFilters()
        }
        requireViewById<Chip>(R.id.mobile_esims_tab_pending).setOnClickListener {
            selectedFilter = EsimFilter.PENDING
            initialFilter = null
            applyFilters()
        }
        requireViewById<Chip>(R.id.mobile_esims_tab_expired).setOnClickListener {
            selectedFilter = EsimFilter.EXPIRED
            initialFilter = null
            applyFilters()
        }
    }

    private fun setupSearch() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun loadEsims() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val result = runCatching { authApi.esims(session) }
            setLoading(false)

            result
                .onSuccess {
                    allEsims = it.esims
                    applyFilters()
                }
                .onFailure {
                    error.text = it.message ?: getString(R.string.mobile_esims_load_failed)
                    error.visibility = View.VISIBLE
                }
        }
    }

    private suspend fun activeSessionOrReturnToLogin(): AuthSession? {
        val savedSession = withContext(Dispatchers.IO) {
            tokenStore.getSession()
        } ?: return redirectToLogin()

        if (!JwtUtils.isExpired(savedSession.accessToken)) return savedSession

        val refreshed = runCatching {
            authApi.refresh(savedSession)
        }.getOrNull() ?: return redirectToLogin()

        withContext(Dispatchers.IO) {
            tokenStore.save(refreshed)
        }
        return refreshed
    }

    private fun applyFilters() {
        val query = search.text?.toString()?.trim().orEmpty().lowercase()
        val baseEsims = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            allEsims.filter { MobileEsimFilters.isExpiredSoon(it) }
        } else {
            allEsims
        }
        val statusFiltered = if (initialFilter == MobileEsimFilters.FILTER_EXPIRED_SOON) {
            baseEsims
        } else {
            baseEsims.filter { selectedFilter.matches(realStatus(it)) }
        }

        val filtered = statusFiltered
            .filter { esim ->
                val displayStatus = realStatus(esim)
                query.isBlank() || listOfNotNull(
                    esim.iccid,
                    esim.provider,
                    PackageNameCleaner.clean(esim.packageName),
                    esim.orderNumber,
                    esim.status,
                    displayStatus.label
                ).any { it.lowercase().contains(query) }
            }
        renderEsims(filtered)
    }

    private fun renderEsims(esimData: List<MobileEsim>) {
        esims.removeAllViews()
        empty.visibility = if (esimData.isEmpty()) View.VISIBLE else View.GONE
        if (esimData.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        esimData.forEach { esim ->
            val item = inflater.inflate(R.layout.mobile_esim_list_item, esims, false)
            val displayStatus = realStatus(esim)
            item.requireViewById<TextView>(R.id.mobile_esim_subtitle).text = esim.iccid.orEmpty().ifBlank { "Pending ICCID" }
            item.requireViewById<TextView>(R.id.mobile_esim_title).text = esim.title()
            item.requireViewById<TextView>(R.id.mobile_esim_meta).text = listOfNotNull(
                visibleProvider(esim.provider)?.takeIf { it.isNotBlank() },
                esim.expiresAt?.takeIf { it.isNotBlank() }?.let { "Expires: ${formatEsimDate(it)}" },
                esim.dataRemaining?.takeIf { it.isNotBlank() }?.let { "Remaining: $it" }
            ).joinToString("  •  ")
            item.requireViewById<TextView>(R.id.mobile_esim_status).apply {
                applyRoamStatusChip(displayStatus.label, displayStatus.raw)
            }
            item.requireViewById<MaterialButton>(R.id.mobile_esim_view_detail).setOnClickListener {
                startActivity(MobileEsimDetailActivity.createIntent(this, esim))
            }
            item.requireViewById<MaterialButton>(R.id.mobile_esim_renew).apply {
                visibility = if (canRenew(esim, displayStatus)) View.VISIBLE else View.GONE
                setOnClickListener { openRenewal(esim) }
            }
            item.setOnClickListener {
                startActivity(MobileEsimDetailActivity.createIntent(this, esim))
            }
            esims.addView(item)
        }
    }


    private fun visibleProvider(provider: String?): String? =
        provider?.replace("TGT", "Orange", ignoreCase = true)
            ?.replace("tgt", "Orange", ignoreCase = true)

    private fun formatEsimDate(value: String): String {
        val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return value
        return parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
    }

    private fun realStatus(esim: MobileEsim): DisplayStatus {
        val raw = esim.status.orEmpty().trim()
        val normalized = raw.lowercase()
        val expiresAt = parseDate(esim.expiresAt)
        val isExpiredByDate = expiresAt?.isBefore(OffsetDateTime.now()) == true
        val hasIccid = !esim.iccid.isNullOrBlank()
        val hasInstallCode = !esim.installCode().isNullOrBlank() || !esim.qrPayload().isNullOrBlank()

        return when {
            normalized.contains("expired") || normalized.contains("depleted") || normalized.contains("terminated") || isExpiredByDate ->
                DisplayStatus("Expired", "expired")
            normalized.contains("active") || normalized.contains("activated") || normalized.contains("enabled") ->
                DisplayStatus("Active", "active")
            normalized.contains("pending") || normalized.contains("processing") || normalized.contains("waiting") || normalized.contains("ordered") ->
                DisplayStatus("Pending", "pending")
            hasIccid && hasInstallCode && expiresAt != null ->
                DisplayStatus("Ready", "ready")
            hasIccid && expiresAt != null ->
                DisplayStatus("Active", "active")
            hasIccid && hasInstallCode ->
                DisplayStatus("Ready", "ready")
            hasInstallCode ->
                DisplayStatus("Ready to Install", "ready")
            hasIccid ->
                DisplayStatus("Provisioned", raw.ifBlank { "provisioned" })
            else ->
                DisplayStatus("Pending", "pending")
        }
    }

    private fun parseDate(value: String?): OffsetDateTime? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    private fun canRenew(esim: MobileEsim, displayStatus: DisplayStatus = realStatus(esim)): Boolean {
        val provider = esim.provider.orEmpty().lowercase()
        if (displayStatus.raw == "expired") return false
        return provider.contains("tgt") || provider.contains("airhub") || provider.contains("vodafone")
    }

    private fun openRenewal(esim: MobileEsim) {
        val provider = esim.provider.orEmpty().lowercase()
        if (provider.contains("airhub") || provider.contains("vodafone")) {
            startActivity(Intent(this, VodafoneRenewalActivity::class.java).apply {
                putExtra("renew.iccid", esim.iccid)
            })
        } else {
            startActivity(Intent(this, TgtSimRechargeActivity::class.java).apply {
                putExtra("renew.iccid", esim.iccid)
            })
        }
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
    }

    private fun openDashboardActivity() {
        startActivity(
            Intent(this, DashboardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openWalletActivity() {
        startActivity(
            Intent(this, WalletActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openPackagesActivity() {
        startActivity(
            Intent(this, PackagesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openMoreActivity() {
        startActivity(
            Intent(this, MoreActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    private fun openNativeEsimActivity() {
        val target = targetActivityName(DashboardActivity.META_ESIM_ACTIVITY)
        if (target.isNullOrBlank()) {
            error.text = getString(R.string.dashboard_missing_esim_target)
            error.visibility = View.VISIBLE
            return
        }
        startActivity(Intent().setClassName(this, target))
    }

    private fun redirectToLogin(): AuthSession? {
        tokenStore.clear()
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        return null
    }

    private fun targetActivityName(key: String): String? {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(key)
    }

    private data class DisplayStatus(
        val label: String,
        val raw: String
    )

    private enum class EsimFilter {
        ACTIVE,
        PENDING,
        EXPIRED,
        ALL;

        fun matches(status: DisplayStatus): Boolean =
            when (this) {
                ACTIVE -> status.raw == "active" || status.raw == "ready"
                PENDING -> status.raw == "pending"
                EXPIRED -> status.raw == "expired"
                ALL -> true
            }
    }
}
