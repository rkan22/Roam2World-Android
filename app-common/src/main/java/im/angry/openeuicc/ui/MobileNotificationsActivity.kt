package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.auth.AuthSession
import im.angry.openeuicc.auth.AuthTokenStore
import im.angry.openeuicc.auth.JwtUtils
import im.angry.openeuicc.auth.MobileNotification
import im.angry.openeuicc.auth.Roam2WorldAuthApi
import im.angry.openeuicc.common.BuildConfig
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileNotificationsActivity : AppCompatActivity() {
    private val tokenStore by lazy { AuthTokenStore(this) }
    private val authApi by lazy { Roam2WorldAuthApi(BuildConfig.ROAM2WORLD_API_BASE_URL) }

    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView
    private lateinit var error: TextView
    private lateinit var summary: TextView
    private lateinit var markAll: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mobile_notifications)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Notifications"
            setDisplayHomeAsUpEnabled(true)
        }

        refresh = requireViewById(R.id.mobile_notifications_refresh)
        list = requireViewById(R.id.mobile_notifications_list)
        empty = requireViewById(R.id.mobile_notifications_empty)
        error = requireViewById(R.id.mobile_notifications_error)
        summary = requireViewById(R.id.mobile_notifications_summary)
        markAll = requireViewById(R.id.mobile_notifications_mark_all)

        setupInsets()
        refresh.setOnRefreshListener { loadNotifications() }
        markAll.setOnClickListener { markAllRead() }
        loadNotifications()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(refresh)
            ),
            consume = false
        )
    }

    private fun loadNotifications() {
        lifecycleScope.launch {
            error.visibility = View.GONE
            empty.visibility = View.GONE
            setLoading(true)
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val result = runCatching { authApi.mobileNotifications(session) }
            setLoading(false)

            result
                .onSuccess {
                    summary.text = "${it.notifications.size} notifications • ${it.unreadCount} unread"
                    markAll.visibility = if (it.unreadCount > 0) View.VISIBLE else View.GONE
                    renderNotifications(it.notifications)
                }
                .onFailure {
                    error.text = it.message ?: "Notifications could not be loaded"
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun renderNotifications(notifications: List<MobileNotification>) {
        list.removeAllViews()
        empty.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        if (notifications.isEmpty()) return

        val inflater = LayoutInflater.from(this)
        notifications.forEach { notification ->
            val item = inflater.inflate(R.layout.mobile_notification_item, list, false)
            item.requireViewById<TextView>(R.id.mobile_notification_title).text = notification.title.orEmpty()
            item.requireViewById<TextView>(R.id.mobile_notification_message).apply {
                text = notification.message.orEmpty()
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            item.requireViewById<TextView>(R.id.mobile_notification_status).text =
                if (notification.isRead) "Read" else "New"
            item.requireViewById<TextView>(R.id.mobile_notification_meta).text = listOfNotNull(
                notification.type?.replace("_", " ")?.uppercase(),
                notification.createdAt,
                notification.relatedOrderId?.let { "Order #$it" },
                notification.relatedEsimId?.let { "eSIM #$it" }
            ).joinToString(" • ")

            item.alpha = if (notification.isRead) 0.72f else 1.0f
            item.setOnClickListener {
                openNotification(notification)
            }
            list.addView(item)
        }
    }


    private fun openNotification(notification: MobileNotification) {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            val notificationId = notification.id
            if (!notification.isRead && !notificationId.isNullOrBlank()) {
                runCatching { authApi.markNotificationRead(session, notificationId) }
            }

            val orderId = notification.relatedOrderId
            val esimId = notification.relatedEsimId

            when {
                !orderId.isNullOrBlank() -> {
                    startActivity(
                        Intent(this@MobileNotificationsActivity, MobileOrderDetailActivity::class.java)
                            .putExtra("mobile_order.id", orderId)
                    )
                }
                !esimId.isNullOrBlank() -> {
                    startActivity(MobileEsimDetailActivity.createIntent(this@MobileNotificationsActivity, esimId))
                }
                else -> {
                    loadNotifications()
                }
            }
        }
    }

    private fun markRead(notificationId: String) {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            runCatching { authApi.markNotificationRead(session, notificationId) }
                .onSuccess { loadNotifications() }
                .onFailure {
                    error.text = it.message ?: "Notification could not be updated"
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun markAllRead() {
        lifecycleScope.launch {
            val session = activeSessionOrReturnToLogin() ?: return@launch
            runCatching { authApi.markAllNotificationsRead(session) }
                .onSuccess { loadNotifications() }
                .onFailure {
                    error.text = it.message ?: "Notifications could not be updated"
                    error.visibility = View.VISIBLE
                }
        }
    }

    private fun setLoading(loading: Boolean) {
        refresh.isRefreshing = loading
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
}
