package im.angry.openeuicc.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.activityToolbarInsetHandler
import im.angry.openeuicc.util.mainViewPaddingInsetHandler
import im.angry.openeuicc.util.setupRootViewSystemBarInsets

class TgtSimRechargeActivity : AppCompatActivity() {
    private lateinit var scroll: View
    private lateinit var selectedPackage: TextView
    private lateinit var iccidLayout: TextInputLayout
    private lateinit var customerNameLayout: TextInputLayout
    private lateinit var customerPhoneLayout: TextInputLayout
    private lateinit var iccid: TextInputEditText
    private lateinit var customerName: TextInputEditText
    private lateinit var customerPhone: TextInputEditText
    private lateinit var activate: MaterialButton

    private var selectedPackageName = "10GB / 30 Days"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tgt_sim_recharge)
        setSupportActionBar(requireViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.r2w_tgt_recharge)

        scroll = contentRoot().getChildAt(1)
        selectedPackage = requireViewById(R.id.tgt_selected_package)
        iccidLayout = requireViewById(R.id.tgt_iccid_layout)
        customerNameLayout = requireViewById(R.id.tgt_customer_name_layout)
        customerPhoneLayout = requireViewById(R.id.tgt_customer_phone_layout)
        iccid = requireViewById(R.id.tgt_iccid)
        customerName = requireViewById(R.id.tgt_customer_name)
        customerPhone = requireViewById(R.id.tgt_customer_phone)
        activate = requireViewById(R.id.tgt_activate)

        setupInsets()
        setupPackageSelection()
        setupActivation()
        renderSelectedPackage()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupInsets() {
        setupRootViewSystemBarInsets(
            window.decorView.rootView,
            arrayOf(
                this::activityToolbarInsetHandler,
                mainViewPaddingInsetHandler(scroll)
            ),
            consume = false
        )
    }

    private fun setupPackageSelection() {
        listOf(
            R.id.tgt_package_10gb_30d to "10GB / 30 Days",
            R.id.tgt_package_20gb_30d to "20GB / 30 Days",
            R.id.tgt_package_30gb_30d to "30GB / 30 Days",
            R.id.tgt_package_50gb_30d to "50GB / 30 Days",
            R.id.tgt_package_20gb_60d to "20GB / 60 Days",
            R.id.tgt_package_60gb_60d to "60GB / 60 Days"
        ).forEach { (chipId, packageName) ->
            requireViewById<Chip>(chipId).setOnClickListener {
                selectedPackageName = packageName
                renderSelectedPackage()
            }
        }
    }

    private fun setupActivation() {
        activate.setOnClickListener {
            if (!validateForm()) return@setOnClickListener

            Toast.makeText(
                this,
                "TGT recharge request ready: $selectedPackageName",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun renderSelectedPackage() {
        selectedPackage.text = "Selected package: $selectedPackageName"
    }

    private fun validateForm(): Boolean {
        clearErrors()

        val iccidValue = iccid.text?.toString()?.trim().orEmpty()
        val customerNameValue = customerName.text?.toString()?.trim().orEmpty()
        val customerPhoneValue = customerPhone.text?.toString()?.trim().orEmpty()

        var valid = true

        if (iccidValue.length < 10) {
            iccidLayout.error = "Enter a valid ICCID"
            valid = false
        }

        if (customerNameValue.isBlank()) {
            customerNameLayout.error = "Customer name is required"
            valid = false
        }

        if (customerPhoneValue.length < 6) {
            customerPhoneLayout.error = "Enter a valid phone number"
            valid = false
        }

        return valid
    }

    private fun clearErrors() {
        iccidLayout.error = null
        customerNameLayout.error = null
        customerPhoneLayout.error = null
    }

    private fun contentRoot(): ViewGroup =
        (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup)
}
