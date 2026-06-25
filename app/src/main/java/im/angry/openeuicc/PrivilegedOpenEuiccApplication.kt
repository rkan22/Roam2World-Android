package im.angry.openeuicc

class PrivilegedOpenEuiccApplication : OpenEuiccApplication() {
    override fun onCreate() {
        // Do NOT call super.onCreate() here.
        // Original OpenEUICC startup touches privileged Telephony/eUICC APIs.
        // Roam2World admin/login flow does not need eUICC startup.
    }
}
