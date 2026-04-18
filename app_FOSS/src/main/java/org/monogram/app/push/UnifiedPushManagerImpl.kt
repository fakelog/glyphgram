package org.monogram.app.push

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.monogram.domain.repository.UnifiedPushManager
import org.monogram.domain.repository.UnifiedPushStatus
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.ResolvedDistributor

class UnifiedPushManagerImpl(
    private val context: Context
) : UnifiedPushManager {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _endpoint = MutableStateFlow(loadEndpoint())
    override val endpoint: StateFlow<String?> = _endpoint.asStateFlow()

    private val _status = MutableStateFlow(loadStatus())
    override val status: StateFlow<UnifiedPushStatus> = _status.asStateFlow()

    override fun isDistributorAvailable(): Boolean = UnifiedPush.getDistributors(context).isNotEmpty()

    override fun getDistributors(): List<String> = UnifiedPush.getDistributors(context)

    override fun getSavedDistributor(): String? = UnifiedPush.getSavedDistributor(context)

    override fun getAckDistributor(): String? = UnifiedPush.getAckDistributor(context)

    fun currentDistributor(): String? {
        val distributors = getDistributors()
        val ack = getAckDistributor()
        if (!ack.isNullOrBlank() && distributors.contains(ack)) {
            Log.d(TAG, "Select distributor from ack: $ack")
            return ack
        }

        val saved = getSavedDistributor()
        if (!saved.isNullOrBlank() && distributors.contains(saved)) {
            Log.d(TAG, "Select distributor from saved: $saved")
            return saved
        }

        val resolved = UnifiedPush.resolveDefaultDistributor(context)
        if (resolved is ResolvedDistributor.Found && distributors.contains(resolved.packageName)) {
            Log.d(TAG, "Select distributor from default resolver: ${resolved.packageName}")
            return resolved.packageName
        }

        val first = distributors.firstOrNull()
        if (first != null) {
            Log.d(TAG, "Select distributor fallback first installed: $first")
        }
        return first
    }

    override fun ensureRegistered(force: Boolean): Boolean {
        val distributor = currentDistributor() ?: return false
        val endpointKnown = !_endpoint.value.isNullOrBlank()
        if (!force && endpointKnown && _status.value == UnifiedPushStatus.REGISTERED && !shouldRefreshRegistration()) {
            Log.d(TAG, "Skip re-register: endpoint already known and fresh")
            return true
        }

        _status.value = UnifiedPushStatus.REGISTERING
        markRegistrationAttempt()
        Log.d(
            TAG,
            "Request UnifiedPush register: distributor=$distributor force=$force endpointKnown=$endpointKnown"
        )

        return runCatching {
            UnifiedPush.saveDistributor(context, distributor)
            UnifiedPush.register(context, INSTANCE_ID)
        }.onFailure {
            _status.value = UnifiedPushStatus.FAILED
            Log.e(TAG, "Failed to request UnifiedPush registration", it)
        }.isSuccess
    }

    override fun unregister() {
        runCatching {
            UnifiedPush.unregister(context, INSTANCE_ID)
        }.onFailure {
            Log.e(TAG, "Failed to request UnifiedPush unregister", it)
        }
        clearEndpoint()
        _status.value = UnifiedPushStatus.UNREGISTERED
    }

    fun onNewEndpoint(endpoint: PushEndpoint) {
        onNewEndpoint(endpoint.url)
    }

    fun onNewEndpoint(endpoint: String?) {
        val value = endpoint?.trim().orEmpty()
        if (value.isEmpty()) {
            _status.value = UnifiedPushStatus.FAILED
            return
        }

        prefs.edit()
            .putString(KEY_ENDPOINT, value)
            .putLong(KEY_LAST_REGISTERED_AT, System.currentTimeMillis())
            .apply()
        _endpoint.value = value
        _status.value = UnifiedPushStatus.REGISTERED
        Log.d(TAG, "UnifiedPush endpoint saved: ${value.take(140)}")
    }

    fun onRegistrationFailed(reason: FailedReason?) {
        _status.value = UnifiedPushStatus.FAILED
        if (reason != null) {
            Log.w(TAG, "UnifiedPush registration failed: $reason")
        }
    }

    fun onTempUnavailable() {
        _status.value = UnifiedPushStatus.FAILED
        Log.w(TAG, "UnifiedPush distributor temporarily unavailable")
    }

    fun onUnregistered() {
        clearEndpoint()
        _status.value = UnifiedPushStatus.UNREGISTERED
    }

    fun shouldRefreshRegistration(): Boolean {
        val last = prefs.getLong(KEY_LAST_REGISTERED_AT, 0L)
        if (last <= 0L) return true
        return System.currentTimeMillis() - last >= REFRESH_INTERVAL_MS
    }

    private fun clearEndpoint() {
        prefs.edit().remove(KEY_ENDPOINT).apply()
        _endpoint.value = null
    }

    private fun markRegistrationAttempt() {
        prefs.edit().putLong(KEY_LAST_REGISTER_ATTEMPT_AT, System.currentTimeMillis()).apply()
    }

    private fun loadEndpoint(): String? =
        prefs.getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() }

    private fun loadStatus(): UnifiedPushStatus {
        return if (_endpoint.value.isNullOrBlank()) UnifiedPushStatus.IDLE else UnifiedPushStatus.REGISTERED
    }

    private companion object {
        const val TAG = "UnifiedPushManager"
        const val PREFS_NAME = "unified_push_state"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_LAST_REGISTERED_AT = "last_registered_at"
        const val KEY_LAST_REGISTER_ATTEMPT_AT = "last_register_attempt_at"
        const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val INSTANCE_ID = "monogram_default"
    }
}
