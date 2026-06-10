package im.angry.openeuicc.auth

data class MobileNotificationList(
    val unreadCount: Int,
    val notifications: List<MobileNotification>
)

data class MobileNotification(
    val id: String?,
    val title: String?,
    val message: String?,
    val type: String?,
    val isRead: Boolean,
    val readAt: String?,
    val createdAt: String?,
    val relatedOrderId: String?,
    val relatedEsimId: String?
)
