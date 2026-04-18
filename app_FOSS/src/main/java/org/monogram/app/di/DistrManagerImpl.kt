package org.monogram.app.di

import android.content.Context
import android.os.Build
import org.monogram.domain.managers.DistrManager
import org.monogram.domain.repository.UnifiedPushManager

class DistrManagerImpl(
    private val context: Context,
    private val unifiedPushManager: UnifiedPushManager
) : DistrManager {
    override fun isGmsAvailable(): Boolean = false

    override fun isFcmAvailable(): Boolean = false

    override fun isUnifiedPushDistributorAvailable(): Boolean {
        return unifiedPushManager.isDistributorAvailable()
    }

    override fun isInstalledFromGooglePlay(): Boolean {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        return installer == "com.android.vending"
    }
}
