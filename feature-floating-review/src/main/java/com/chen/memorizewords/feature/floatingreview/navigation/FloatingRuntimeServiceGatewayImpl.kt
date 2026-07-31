package com.chen.memorizewords.feature.floatingreview.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.chen.memorizewords.core.navigation.FloatingWordActions
import com.chen.memorizewords.core.navigation.FloatingWordEntryExtras
import com.chen.memorizewords.domain.floating.model.FloatingRuntimeSession
import com.chen.memorizewords.domain.floating.service.FloatingRuntimeServiceGateway
import com.chen.memorizewords.feature.floatingreview.ui.floating.FloatingWordService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FloatingRuntimeServiceGatewayImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FloatingRuntimeServiceGateway {

    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun dispatchStart(session: FloatingRuntimeSession): Result<Unit> = runCatching {
        ContextCompat.startForegroundService(
            context,
            serviceIntent(FloatingWordActions.ACTION_START, session)
        )
    }

    override fun dispatchStop(session: FloatingRuntimeSession?): Result<Unit> = runCatching {
        if (session == null) {
            context.stopService(Intent(context, FloatingWordService::class.java))
            return@runCatching
        }
        val intent = serviceIntent(FloatingWordActions.ACTION_STOP, session)
        context.startService(intent)
    }

    override fun dispatchReconfigure(session: FloatingRuntimeSession): Result<Unit> = runCatching {
        context.startService(serviceIntent(FloatingWordActions.ACTION_RECONFIGURE, session))
    }

    private fun serviceIntent(action: String, session: FloatingRuntimeSession): Intent {
        return Intent(context, FloatingWordService::class.java).apply {
            this.action = action
            putExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_SESSION_ID, session.sessionId)
            putExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_REVISION, session.revision)
            putExtra(FloatingWordEntryExtras.EXTRA_RUNTIME_CONFIG_VERSION, session.configVersion)
        }
    }
}
