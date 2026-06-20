package im.angry.openeuicc.auth

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

object MobileEsimFilters {
    const val FILTER_EXTRA_KEY = "filter"
    const val FILTER_EXPIRED_SOON = "expired_soon"
    private const val EXPIRED_SOON_DAYS = 7L

    fun isExpiredSoon(esim: MobileEsim, now: Instant = Instant.now()): Boolean {
        val expiresAt = parseExpiry(esim.expiresAt) ?: return false
        if (expiresAt.isBefore(now)) return false
        val daysUntilExpiry = ChronoUnit.DAYS.between(
            now.atOffset(ZoneOffset.UTC),
            expiresAt.atOffset(ZoneOffset.UTC)
        )
        return daysUntilExpiry in 0..EXPIRED_SOON_DAYS &&
            esim.status?.equals("expired", ignoreCase = true) != true
    }

    private fun parseExpiry(value: String?): Instant? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    }
}
