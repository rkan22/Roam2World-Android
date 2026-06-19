package im.angry.openeuicc.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.widget.Toast

object R2wUsbPermissionHelper {
    private const val ACTION_USB_PERMISSION = "im.angry.easyeuicc.R2W_USB_PERMISSION"
    private const val USB_CLASS_SMART_CARD = 11

    fun requestAttachedUsbReaders(activity: Activity, showToast: Boolean = false): Int {
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return 0
        val readers = usbManager.deviceList.values
            .filter { it.looksLikeSmartCardReader() }
            .filterNot { usbManager.hasPermission(it) }

        if (readers.isEmpty()) return 0

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val permissionIntent = PendingIntent.getBroadcast(
            activity,
            2407,
            Intent(ACTION_USB_PERMISSION).setPackage(activity.packageName),
            flags
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (showToast) {
                    Toast.makeText(
                        context,
                        if (granted) {
                            "USB kart okuyucu izni verildi. Tekrar deneyebilirsin."
                        } else {
                            "USB kart okuyucu izni verilmedi."
                        },
                        Toast.LENGTH_LONG
                    ).show()
                }
                runCatching { context.unregisterReceiver(this) }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(receiver, filter)
        }

        readers.forEach { usbManager.requestPermission(it, permissionIntent) }

        if (showToast) {
            Toast.makeText(
                activity,
                "USB kart okuyucu bulundu. Android izin penceresini onayla.",
                Toast.LENGTH_LONG
            ).show()
        }

        return readers.size
    }

    private fun UsbDevice.looksLikeSmartCardReader(): Boolean {
        if (deviceClass == USB_CLASS_SMART_CARD) return true
        return (0 until interfaceCount).any { index ->
            getInterface(index).interfaceClass == USB_CLASS_SMART_CARD
        }
    }
}
