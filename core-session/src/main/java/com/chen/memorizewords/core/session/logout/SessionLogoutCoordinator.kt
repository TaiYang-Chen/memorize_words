package com.chen.memorizewords.core.session.logout

import com.chen.memorizewords.core.common.coroutines.ApplicationScope
import com.chen.memorizewords.core.common.coroutines.MainImmediateDispatcher
import com.chen.memorizewords.domain.account.model.LogoutOutcome
import com.chen.memorizewords.domain.account.repository.user.LogoutDataLossRiskException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class SessionLogoutCoordinator @Inject constructor(
    private val executor: SessionLogoutExecutor,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:MainImmediateDispatcher private val mainDispatcher: CoroutineDispatcher
) {

    private val stateLock = Any()
    private val requestIds = AtomicLong(0L)
    private val hostIds = AtomicLong(0L)
    private val _state = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val state: StateFlow<LogoutState> = _state.asStateFlow()

    fun createHostId(): LogoutHostId = LogoutHostId(hostIds.incrementAndGet())

    fun requestLogout() {
        synchronized(stateLock) {
            if (_state.value != LogoutState.Idle) return
            _state.value = LogoutState.AwaitingLoadingFrame(
                requestId = requestIds.incrementAndGet(),
                force = false
            )
        }
    }

    fun claimLoadingHost(requestId: Long, hostId: LogoutHostId): Boolean {
        return synchronized(stateLock) {
            when (val current = _state.value) {
                is LogoutState.AwaitingLoadingFrame -> {
                    if (current.requestId != requestId) return@synchronized false
                    when (current.claimedBy) {
                        null -> {
                            _state.value = current.copy(claimedBy = hostId)
                            true
                        }
                        hostId -> true
                        else -> false
                    }
                }
                is LogoutState.Executing -> {
                    if (current.requestId != requestId) return@synchronized false
                    when (current.claimedBy) {
                        null -> {
                            _state.value = current.copy(claimedBy = hostId)
                            true
                        }
                        hostId -> true
                        else -> false
                    }
                }
                else -> false
            }
        }
    }

    fun releaseLoadingHost(requestId: Long, hostId: LogoutHostId) {
        synchronized(stateLock) {
            when (val current = _state.value) {
                is LogoutState.AwaitingLoadingFrame -> {
                    if (current.requestId == requestId && current.claimedBy == hostId) {
                        _state.value = current.copy(claimedBy = null)
                    }
                }
                is LogoutState.Executing -> {
                    if (current.requestId == requestId && current.claimedBy == hostId) {
                        _state.value = current.copy(claimedBy = null)
                    }
                }
                else -> Unit
            }
        }
    }

    fun onLoadingFramePresented(requestId: Long, hostId: LogoutHostId) {
        val force = synchronized(stateLock) {
            val current = _state.value as? LogoutState.AwaitingLoadingFrame ?: return
            if (current.requestId != requestId || current.claimedBy != hostId) return
            _state.value = LogoutState.Executing(requestId, current.force, hostId)
            current.force
        }
        applicationScope.launch(mainDispatcher) {
            val result = executor.execute(force)
            synchronized(stateLock) {
                val current = _state.value as? LogoutState.Executing
                if (current?.requestId != requestId) return@synchronized

                val failure = result.exceptionOrNull()
                _state.value = when {
                    !force && failure is LogoutDataLossRiskException -> {
                        LogoutState.AwaitingRiskConfirmation(requestId)
                    }
                    failure != null && !force -> LogoutState.Failed(requestId, failure)
                    failure != null -> LogoutState.ReadyToNavigate(
                        requestId = requestId,
                        terminal = LogoutTerminal.ForceFailure(failure)
                    )
                    else -> LogoutState.ReadyToNavigate(
                        requestId = requestId,
                        terminal = LogoutTerminal.Success(result.getOrThrow())
                    )
                }
            }
        }
    }

    fun claimRiskDialog(requestId: Long, hostId: LogoutHostId): Boolean {
        return synchronized(stateLock) {
            val current = _state.value as? LogoutState.AwaitingRiskConfirmation
                ?: return@synchronized false
            if (current.requestId != requestId) return@synchronized false
            when (current.claimedBy) {
                null -> {
                    _state.value = current.copy(claimedBy = hostId)
                    true
                }
                hostId -> true
                else -> false
            }
        }
    }

    fun releaseRiskDialog(requestId: Long, hostId: LogoutHostId) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.AwaitingRiskConfirmation ?: return
            if (current.requestId == requestId && current.claimedBy == hostId) {
                _state.value = current.copy(claimedBy = null)
            }
        }
    }

    fun confirmDataLossRisk(requestId: Long) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.AwaitingRiskConfirmation ?: return
            if (current.requestId != requestId) return
            _state.value = LogoutState.AwaitingLoadingFrame(
                requestId = requestIds.incrementAndGet(),
                force = true
            )
        }
    }

    fun cancelDataLossRisk(requestId: Long) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.AwaitingRiskConfirmation ?: return
            if (current.requestId == requestId) {
                _state.value = LogoutState.Idle
            }
        }
    }

    fun claimTerminal(hostId: LogoutHostId): LogoutTerminalLease? {
        return synchronized(stateLock) {
            when (val current = _state.value) {
                is LogoutState.ReadyToNavigate -> {
                    if (current.claimedBy != null) return@synchronized null
                    _state.value = current.copy(claimedBy = hostId)
                    LogoutTerminalLease.Navigation(current.requestId, hostId, current.terminal)
                }
                is LogoutState.Failed -> {
                    if (current.claimedBy != null) return@synchronized null
                    _state.value = current.copy(claimedBy = hostId)
                    LogoutTerminalLease.Failure(current.requestId, hostId, current.failure)
                }
                else -> null
            }
        }
    }

    fun releaseTerminal(lease: LogoutTerminalLease) {
        synchronized(stateLock) {
            when (val current = _state.value) {
                is LogoutState.ReadyToNavigate -> if (lease is LogoutTerminalLease.Navigation &&
                    current.requestId == lease.requestId && current.claimedBy == lease.hostId
                ) {
                    _state.value = current.copy(claimedBy = null)
                }
                is LogoutState.Failed -> if (lease is LogoutTerminalLease.Failure &&
                    current.requestId == lease.requestId && current.claimedBy == lease.hostId
                ) {
                    _state.value = current.copy(claimedBy = null)
                }
                else -> Unit
            }
        }
    }

    fun beginNavigation(lease: LogoutTerminalLease.Navigation): Boolean {
        return synchronized(stateLock) {
            val current = _state.value as? LogoutState.ReadyToNavigate
                ?: return@synchronized false
            if (current.requestId != lease.requestId || current.claimedBy != lease.hostId) {
                return@synchronized false
            }
            _state.value = LogoutState.Navigating(lease.requestId, lease.hostId)
            true
        }
    }

    fun navigationFailed(lease: LogoutTerminalLease.Navigation) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.Navigating ?: return
            if (current.requestId == lease.requestId && current.hostId == lease.hostId) {
                _state.value = LogoutState.ReadyToNavigate(lease.requestId, lease.terminal)
            }
        }
    }

    fun navigationHostStopped(lease: LogoutTerminalLease.Navigation) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.Navigating ?: return
            if (current.requestId == lease.requestId && current.hostId == lease.hostId) {
                _state.value = LogoutState.Idle
            }
        }
    }

    fun failureHandled(lease: LogoutTerminalLease.Failure) {
        synchronized(stateLock) {
            val current = _state.value as? LogoutState.Failed ?: return
            if (current.requestId == lease.requestId && current.claimedBy == lease.hostId) {
                _state.value = LogoutState.Idle
            }
        }
    }
}

@JvmInline
value class LogoutHostId(val value: Long)

sealed interface LogoutTerminalLease {
    val requestId: Long
    val hostId: LogoutHostId

    data class Navigation(
        override val requestId: Long,
        override val hostId: LogoutHostId,
        val terminal: LogoutTerminal
    ) : LogoutTerminalLease

    data class Failure(
        override val requestId: Long,
        override val hostId: LogoutHostId,
        val failure: Throwable
    ) : LogoutTerminalLease
}

sealed interface LogoutState {
    data object Idle : LogoutState
    data class AwaitingLoadingFrame(
        val requestId: Long,
        val force: Boolean,
        val claimedBy: LogoutHostId? = null
    ) : LogoutState
    data class Executing(
        val requestId: Long,
        val force: Boolean,
        val claimedBy: LogoutHostId? = null
    ) : LogoutState
    data class AwaitingRiskConfirmation(
        val requestId: Long,
        val claimedBy: LogoutHostId? = null
    ) : LogoutState
    data class ReadyToNavigate(
        val requestId: Long,
        val terminal: LogoutTerminal,
        val claimedBy: LogoutHostId? = null
    ) : LogoutState
    data class Failed(
        val requestId: Long,
        val failure: Throwable,
        val claimedBy: LogoutHostId? = null
    ) : LogoutState
    data class Navigating(val requestId: Long, val hostId: LogoutHostId) : LogoutState
}

sealed interface LogoutTerminal {
    data class Success(val outcome: LogoutOutcome) : LogoutTerminal
    data class ForceFailure(val failure: Throwable) : LogoutTerminal
}
