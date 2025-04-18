package eu.darken.sdmse.appcontrol.ui.list.actions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.asErrorDialogBuilder
import eu.darken.sdmse.common.lists.differ.update
import eu.darken.sdmse.common.lists.setupDefaults
import eu.darken.sdmse.common.uix.BottomSheetDialogFragment2
import eu.darken.sdmse.databinding.AppcontrolActionDialogBinding

@AndroidEntryPoint
class AppActionDialog : BottomSheetDialogFragment2() {
    override val vm: AppActionViewModel by viewModels()
    override lateinit var ui: AppcontrolActionDialogBinding

    private lateinit var exportPath: ActivityResultLauncher<Intent>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = AppcontrolActionDialogBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED

        exportPath = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                log(TAG, WARN) { "exportPathPickerLauncher returned ${result.resultCode}: ${result.data}" }
                return@registerForActivityResult
            }

            vm.exportApp(result.data?.data)
        }

        val adapter = AppActionAdapter()
        // TODO fix dividers
        ui.recyclerview.setupDefaults(adapter, verticalDividers = false)

        vm.state.observe2(ui) { (appInfo, progress, actions) ->
            icon.loadAppIcon(appInfo.pkg)
            primary.text = appInfo.label.get(requireContext())
            secondary.text = appInfo.pkg.packageName
            tertiary.text = "${appInfo.pkg.versionName ?: "?"} (${appInfo.pkg.versionCode})"
            adapter.update(actions)

            tagContainer.setPkg(appInfo)

            ui.recyclerview.isGone = progress != null
            ui.loadingOverlay.setProgress(progress)
        }

        vm.events.observe2(ui) { event ->
            when (event) {
                is AppActionEvents.SelectExportPath -> {
                    try {
                        exportPath.launch(event.intent)
                    } catch (e: ActivityNotFoundException) {
                        log(TAG, WARN) { "Documents app is missing for $event" }
                        e.asErrorDialogBuilder(requireActivity()).show()
                    }
                }

                is AppActionEvents.ShowResult -> {
                    Snackbar.make(
                        requireView(),
                        event.result.primaryInfo.get(requireContext()),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    companion object {
        private val TAG = logTag("AppControl", "Action", "Dialog")
    }
}