package org.monogram.domain.repository

import kotlinx.coroutines.flow.StateFlow

enum class UnifiedPushStatus {
    IDLE,
    REGISTERING,
    REGISTERED,
    FAILED,
    UNREGISTERED
}

interface UnifiedPushManager {
    val endpoint: StateFlow<String?>
    val status: StateFlow<UnifiedPushStatus>

    fun isDistributorAvailable(): Boolean
    fun getDistributors(): List<String>
    fun getSavedDistributor(): String?
    fun getAckDistributor(): String?
    fun ensureRegistered(force: Boolean = false): Boolean
    fun unregister()
}
