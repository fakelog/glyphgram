package org.monogram.app.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.monogram.domain.repository.UnifiedPushManager
import org.monogram.domain.repository.UnifiedPushStatus

class NoOpUnifiedPushManager : UnifiedPushManager {
    private val _endpoint = MutableStateFlow<String?>(null)
    override val endpoint: StateFlow<String?> = _endpoint

    private val _status = MutableStateFlow(UnifiedPushStatus.IDLE)
    override val status: StateFlow<UnifiedPushStatus> = _status

    override fun isDistributorAvailable(): Boolean = false

    override fun getDistributors(): List<String> = emptyList()

    override fun getSavedDistributor(): String? = null

    override fun getAckDistributor(): String? = null

    override fun ensureRegistered(force: Boolean): Boolean = false

    override fun unregister() {
        _endpoint.value = null
        _status.value = UnifiedPushStatus.UNREGISTERED
    }
}
