package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

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
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_profile)

        avatar = requireViewById(R.id.profile_avatar)
        name = requireViewById(R.id.profile_name)
        email = requireViewById(R.id.profile_email)
        role = requireViewById(R.id.profile_role)
        permissions = requireViewById(R.id.profile_permissions)

        requireViewById<MaterialButton>(R.id.profile_settings).setOnClickListener {
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

        avatar.text = initials(displayName)
        name.text = displayName
        email.text = emailValue
        role.text = "Role: ${roleValue.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
        permissions.text = permissionsForRole(roleValue)
    }

    private fun initials(value: String): String {
        val parts = value.split(" ").filter { it.isNotBlank() }
        return parts.take(2).map { it.first().uppercaseChar() }.joinToString("").ifBlank { "R2W" }
    }

    private fun permissionsForRole(role: String): String = when (role.lowercase()) {
        "admin" -> "All modules • Dealer management • Reports • Wallet • eSIM Store"
        "reseller" -> "Dealer management • Wallet • Reports • eSIM Store • TGT Recharge"
        "dealer" -> "eSIM Store • Wallet • Orders • TGT Recharge"
        else -> "eSIM Store • Wallet • Reports • OpenEUICC"
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
