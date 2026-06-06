package im.angry.openeuicc.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import im.angry.openeuicc.ui.b2b.navigation.B2BNavGraph
import im.angry.openeuicc.ui.b2b.theme.B2BTheme

class B2BMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            B2BTheme {
                val navController = rememberNavController()
                B2BNavGraph(navController = navController)
            }
        }
    }
}
