package im.angry.openeuicc.ui.wizard

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import im.angry.openeuicc.common.R

class DownloadWizardResultFragment : DownloadWizardActivity.DownloadWizardStepFragment() {
    override val hasNext: Boolean = false
    override val hasPrev: Boolean = false

    override fun createNextFragment(): DownloadWizardActivity.DownloadWizardStepFragment? = null
    override fun createPrevFragment(): DownloadWizardActivity.DownloadWizardStepFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_download_result, container, false)
        val title = view.requireViewById<TextView>(R.id.download_result_title)
        val message = view.requireViewById<TextView>(R.id.download_result_message)
        val suggestion = view.requireViewById<TextView>(R.id.download_result_suggestion)
        val primary = view.requireViewById<MaterialButton>(R.id.download_result_primary)
        val diagnostics = view.requireViewById<MaterialButton>(R.id.download_result_diagnostics)

        val error = state.downloadError
        if (error == null) {
            requireActivity().setResult(Activity.RESULT_OK)
            title.text = getString(R.string.download_wizard_success_title)
            message.text = getString(R.string.download_wizard_success_message)
            suggestion.visibility = View.GONE
            diagnostics.visibility = View.GONE
            primary.text = getString(R.string.download_wizard_done)
            primary.setOnClickListener { requireActivity().finish() }
        } else {
            requireActivity().setResult(Activity.RESULT_CANCELED)
            val simplified = SimplifiedErrorMessages.fromDownloadError(error)
            title.text = getString(R.string.download_wizard_failure_title)
            message.text = simplified?.titleResId?.let { getString(it) }
                ?: getString(R.string.download_wizard_failure_message)
            simplified?.suggestResId?.let {
                suggestion.text = getString(it)
                suggestion.visibility = View.VISIBLE
            } ?: run {
                suggestion.visibility = View.GONE
            }
            diagnostics.visibility = View.VISIBLE
            diagnostics.setOnClickListener { gotoNextFragment(DownloadWizardDiagnosticsFragment()) }
            primary.text = getString(R.string.download_wizard_close)
            primary.setOnClickListener { requireActivity().finish() }
        }

        return view
    }
}
