package im.angry.openeuicc

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class AdminActivityLogDetailActivity : Activity() {
    companion object {
        const val EXTRA_LOG_JSON = "extra_log_json"
    }

    private lateinit var subtitleText: TextView
    private lateinit var refreshButton: Button
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_activity_log_detail)

        subtitleText = findViewById(R.id.adminActivityLogDetailSubtitleText)
        refreshButton = findViewById(R.id.adminActivityLogDetailRefreshButton)
        listContainer = findViewById(R.id.adminActivityLogDetailListContainer)

        refreshButton.text = "Close"
        refreshButton.setOnClickListener { finish() }

        val raw = intent.getStringExtra(EXTRA_LOG_JSON)
        if (raw.isNullOrBlank()) {
            subtitleText.text = "No log data"
            addCard("Activity log data was not provided.")
            return
        }

        val log = JSONObject(raw)
        render(log)
    }

    private fun render(log: JSONObject) {
        val title = log.optString("title", "-")
        val type = log.optString("type", "-")
        val status = log.optString("status", "-")
        val actor = log.optString("actor", "-")
        val message = log.optString("message", "-")
        val createdAt = log.optString("created_at", "-")

        subtitleText.text = "$type / $status"

        addCard(
            "Overview\n" +
                "Title: $title\n" +
                "Type: $type\n" +
                statusBadge(status) + "\n" +
                "Actor: $actor\n" +
                "Date: $createdAt"
        )

        addCard(
            "Message\n" +
                message
        )
    }

    private fun statusBadge(status: String): String {
        val normalized = status.lowercase()
        val icon = when {
            normalized.contains("active") && !normalized.contains("inactive") -> ""
            normalized.contains("completed") -> ""
            normalized.contains("resolved") -> ""
            normalized.contains("ok") -> ""
            normalized.contains("open") -> ""
            normalized.contains("pending") -> ""
            normalized.contains("progress") -> ""
            normalized.contains("inactive") -> ""
            normalized.contains("closed") -> ""
            normalized.contains("suspended") -> ""
            normalized.contains("failed") -> ""
            normalized.contains("error") -> ""
            else -> ""
        }
        return "Status: $status"
    }

    private fun addCard(text: String) {
        val card = TextView(this)
        card.text = text
        card.textSize = 15.5f
        card.setTextColor(0xFF07133D.toInt())
        card.setBackgroundResource(R.drawable.admin_card_background)
        card.elevation = 3f
        card.setPadding(28, 24, 28, 24)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 20)
        card.layoutParams = params
        listContainer.addView(card)
    }
}
