package im.angry.openeuicc.ui

import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.readSelfLog
import im.angry.openeuicc.util.selfAppVersion
import im.angry.openeuicc.util.setupLogSaving
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class LogsActivity : ComponentActivity() {
    private var logStr: String = ""
    private var visibleLog by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var refreshKey by mutableIntStateOf(0)

    private val saveLogs =
        setupLogSaving(
            getLogFileName = {
                getString(
                    R.string.logs_filename_template,
                    SimpleDateFormat.getDateTimeInstance().format(Date())
                )
            },
            getLogText = ::buildLogText
        )

    private fun buildLogText() = buildString {
        appendLine("Manufacturer: ${Build.MANUFACTURER}")
        appendLine("Brand: ${Build.BRAND}")
        appendLine("Model: ${Build.MODEL}")
        appendLine("SDK Version: ${Build.VERSION.SDK_INT}")
        appendLine("App Version: $selfAppVersion")
        appendLine("-".repeat(10))
        appendLine(logStr)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LogsScreen(
                visibleLog = visibleLog,
                fullLineCount = logStr.lines().size,
                loading = loading,
                errorMessage = errorMessage,
                refreshKey = refreshKey,
                onBack = { finish() },
                onRefresh = { reload() },
                onSave = { saveLogs() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            loading = true
            errorMessage = null

            val result = runCatching {
                val fullLog = withContext(Dispatchers.IO) {
                    intent.extras?.getString("log") ?: readSelfLog()
                }
                val displayLog = withContext(Dispatchers.Default) {
                    fullLog.lines().takeLast(256).joinToString("\n")
                }
                fullLog to displayLog
            }

            loading = false

            result
                .onSuccess { (fullLog, displayLog) ->
                    logStr = fullLog
                    visibleLog = displayLog
                    refreshKey += 1
                }
                .onFailure {
                    errorMessage = it.message ?: "Logs could not be loaded."
                }
        }
    }
}

@Composable
private fun LogsScreen(
    visibleLog: String,
    fullLineCount: Int,
    loading: Boolean,
    errorMessage: String?,
    refreshKey: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit
) {
    val bg = Color(0xFFF6F7FB)
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()

    LaunchedEffect(refreshKey, visibleLog) {
        vertical.animateScrollTo(vertical.maxValue)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(vertical)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LogsHero(
                        fullLineCount = fullLineCount,
                        visibleLineCount = visibleLog.lines().filter { it.isNotBlank() }.size,
                        loading = loading,
                        onBack = onBack,
                        onRefresh = onRefresh,
                        onSave = onSave
                    )

                    errorMessage?.let {
                        InfoCard(title = "Logs could not be loaded") {
                            Text(it, color = Color(0xFFDC2626))
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Try again")
                            }
                        }
                    }

                    InfoCard(title = "Recent debug logs") {
                        if (loading && visibleLog.isBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF7900)
                                )
                                Text("Reading logs...", color = Color(0xFF6B7280))
                            }
                        } else if (visibleLog.isBlank()) {
                            Text("No logs available.", color = Color(0xFF6B7280))
                        } else {
                            Text(
                                text = visibleLog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontal),
                                color = Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }

                        OutlinedButton(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(if (loading) "Loading..." else "Refresh")
                        }

                        Button(
                            onClick = onSave,
                            enabled = !loading && visibleLog.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsHero(
    fullLineCount: Int,
    visibleLineCount: Int,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17181C))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Logs",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Recent debug logs of the application",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Back",
                        color = Color(0xFFFF7900),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable(onClick = onBack)
                    )
                    Text(
                        if (loading) "Loading..." else "Refresh",
                        color = Color.White.copy(alpha = 0.78f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !loading, onClick = onRefresh)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Color(0xFFFFEFE2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LOG", color = Color(0xFFFF7900), fontWeight = FontWeight.Black)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$visibleLineCount visible lines",
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Full log lines: $fullLineCount • UI shows last 256 lines",
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = !loading && fullLineCount > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7900)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Save logs", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Color(0xFF17181C), fontWeight = FontWeight.Black)
            HorizontalDivider()
            content()
        }
    }
}
