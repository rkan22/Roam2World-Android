package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    private lateinit var scroll: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var avatar: TextView
    private lateinit var name: TextView
    private lateinit var email: TextView
    private lateinit var role: TextView
    private lateinit var permissions: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""

        scroll = requireViewById(R.id.profile_scroll)
        bottomNav = requireViewById(R.id.profile_bottom_nav)
        avatar = requireViewById(R.id.profile_avatar)
        name = requireViewById(R.id.profile_name)
        email = requireViewById(R.id.profile_email)
        role = requireViewById(R.id.profile_role)
        permissions = requireViewById(R.id.profile_permissions)

        setupInsets()
        setupBottomNavigation()

        requireViewById<View>(R.id.profile_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        requireViewById<MaterialButton>(R.id.profile_logout).setOnClickListener {
            logout()
        }

        loadProfile()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll),
                { insets -> bottomNav.updatePadding(insets.left, bottomNav.paddingTop, insets.right, insets.bottom) }
            ),
            consume = false
        )
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_packages -> {
                    startActivity(Intent(this, PackagesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, WalletActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_esims -> {
                    startActivity(Intent(this, MobileEsimsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    false
                }
                R.id.nav_more -> true
                else -> false
            }
        }
        bottomNav.menu.findItem(R.id.nav_more)?.isChecked = true
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { tokenStore.getSession() }
            renderProfile(session)
        }
    }

    private fun renderProfile(session: AuthSession?) {
        val displayName = session?.displayName?.takeIf { it.isNotBlank() } ?: "Roam2World User"
        val emailValue = session?.email?.takeIf { it.isNotBlank() } ?: "user@company.com"
        val roleValue = session?.role?.takeIf { it.isNotBlank() } ?: "B2B User"

        val prettyRole = roleValue.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        avatar.text = initials(displayName)
        name.text = displayName
        email.text = emailValue
        role.text = prettyRole
        permissions.text = permissionsForRole(roleValue)

        findViewById<TextView>(R.id.profile_full_name_value)?.text = displayName
        findViewById<TextView>(R.id.profile_email_value)?.text = emailValue
        findViewById<TextView>(R.id.profile_phone_value)?.text = "Not provided"
        findViewById<TextView>(R.id.profile_account_type)?.text = "Roam2World B2B"
    }

    private fun initials(value: String): String {
        val parts = value.split(" ").filter { it.isNotBlank() }
        return parts.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "R2W" }
    }

    private fun permissionsForRole(role: String): String = when (role.lowercase()) {
        "admin" -> "All modules • Reports • Wallet"
        "reseller" -> "Dealer management • Wallet • Reports"
        "dealer" -> "Store • Wallet • Orders"
        else -> "Store • Wallet • Reports"
    }

    private fun logout() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { tokenStore.clear() }
            startActivity(
                Intent(this@ProfileActivity, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }
}
