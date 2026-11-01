package com.abominableenterprise.cherkit.update.compose

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo

/** [UpdateFlow] over Play In-App Updates: `appUpdateInfo`, then the FLEXIBLE flow through [sheet]. */
internal class PlayUpdateFlow(
    private val manager: AppUpdateManager,
    private val sheet: ActivityResultLauncher<IntentSenderRequest>,
) : UpdateFlow<AppUpdateInfo> {
    override suspend fun request(): AppUpdateInfo = manager.requestAppUpdateInfo()

    override fun status(info: AppUpdateInfo): UpdateStatus = UpdateStatus(
        updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
        flexibleAllowed = info.isUpdateTypeAllowed(FLEXIBLE),
        availableVersionCode = info.availableVersionCode(),
        downloaded = info.installStatus() == InstallStatus.DOWNLOADED,
    )

    override fun startFlexible(info: AppUpdateInfo): Boolean = manager.startUpdateFlowForResult(info, sheet, FLEXIBLE)

    private companion object {
        val FLEXIBLE: AppUpdateOptions = AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
    }
}
