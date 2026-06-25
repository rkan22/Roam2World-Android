package im.angry.openeuicc.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.common.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : ComponentActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                tokenStore.getSession()
            }

            if (session == null || JwtUtils.isExpired(session.accessToken)) {
                withContext(Dispatchers.IO) {
                    tokenStore.clear()
                }
                openAndClear(LoginActivity::class.java.name)
                return@launch
            }

            val role = session.role.orEmpty().trim().lowercase()
            val target = if (role in ADMIN_ROLES) {
                MOBILE_ADMIN_ACTIVITY
            } else {
                loginTargetActivityName()
            }

            openAndClear(target)
        }
    }

    private fun loginTargetActivityName(): String {
        val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        return appInfo.metaData?.getString(LoginActivity.META_TARGET_ACTIVITY)
            ?: DashboardActivity::class.java.name
    }

    private fun openAndClear(targetClassName: String) {
        startActivity(
            Intent().setClassName(this, targetClassName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val MOBILE_ADMIN_ACTIVITY = "im.angry.openeuicc.MobileAdminActivity"
        private val ADMIN_ROLES = setOf("admin", "super_admin", "superadmin", "staff")
    }
}
