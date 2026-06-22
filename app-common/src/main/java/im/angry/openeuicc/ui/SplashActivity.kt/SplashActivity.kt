package im.angry.openeuicc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1263F1)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = im.angry.openeuicc.common.R.drawable.splash_screen),
                        contentDescription = null,
                        modifier = Modifier.size(190.dp),
                        contentScale = ContentScale.Fit
                    )

                    LaunchedEffect(Unit) {
                        delay(2800)
                        openMain()
                    }
                }
            }
        }
    }

    private fun openMain() {
        val candidates = listOf(
            "im.angry.openeuicc.ui.R2wComposeHomeActivity",
            "im.angry.openeuicc.ui.DashboardActivity",
            "im.angry.openeuicc.ui.PackagesActivity"
        )

        val target = candidates.firstOrNull { className ->
            runCatching { Class.forName(className) }.isSuccess
        } ?: "im.angry.openeuicc.ui.PackagesActivity"

        startActivity(Intent().setClassName(this, target))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
