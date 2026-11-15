package io.github.jqssun.airplay

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.ui.MainScreen
import io.github.jqssun.airplay.ui.theme.AirPlayTheme
import io.github.jqssun.airplay.viewmodel.LocalNetworkAccess
import io.github.jqssun.airplay.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var service: AirPlayService? = null
    val isInPip = mutableStateOf(false)
    private val style = mutableStateOf(Triple(0xFF302A23.toInt(), 0xFF111113.toInt(), true))
    private val logCallback: (String) -> Unit = { viewModel.addLog(it) }
    private val pinCallback: (String?) -> Unit = { viewModel.showPin(it) }
    private val sharingEnded: () -> Unit = { _leaveAfterSharing() }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? AirPlayService.LocalBinder)?.service ?: return
            service = svc
            svc.logCallback = logCallback
            svc.pinCallback = pinCallback
            svc.sharingEndedCallback = sharingEnded
            viewModel.bindService(svc)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) _refreshForeground()
            _autoStart()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            viewModel.unbindService()
        }
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (it.isNotEmpty()) _syncLocalNetwork(requested = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val missing = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !_granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!hasLocalNetwork()) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())

        bindService(Intent(this, AirPlayService::class.java), connection, BIND_AUTO_CREATE)

        _applyStyle(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.updateFromService()
                    delay(200)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.pref(Prefs.KEEP_SCREEN_ON), viewModel.connectionCount) { keep, conns ->
                    keep && conns > 0
                }.collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // auto-enter pre-declared in pip params
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        viewModel.serverState, viewModel.connectionCount,
                        viewModel.videoPlaybackActive, viewModel.videoPlaybackAspect,
                        viewModel.videoAspect
                    ) { _, _, _, _, _ -> }.collect { setPictureInPictureParams(_pipParams()) }
                }
            }
        }

        setContent {
            val (skyTop, skyBottom, dark) = style.value
            AirPlayTheme(skyTop, skyBottom, dark) {
                MainScreen(
                    viewModel = viewModel,
                    isInPip = isInPip.value,
                    onSurfaceAvailable = { viewModel.onSurfaceAvailable(it) },
                    onSurfaceDestroyed = { viewModel.onSurfaceDestroyed(it) },
                    onPip = { enterPip() },
                    onLocalNetwork = ::_requestLocalNetwork
                )
            }
        }
    }

    // Optional local UI colors and light/dark from the launcher; launches without them (a sender connecting) keep the
    // last ones. They do not affect receiver settings or transport.
    private fun _applyStyle(intent: Intent) {
        val prefs = getSharedPreferences("puretv_style", MODE_PRIVATE)
        val top = intent.getIntExtra("puretv_sky_top", prefs.getInt("top", 0xFF302A23.toInt())) or 0xFF000000.toInt()
        val bottom = intent.getIntExtra("puretv_sky_bottom", prefs.getInt("bottom", 0xFF111113.toInt())) or 0xFF000000.toInt()
        val dark = intent.getBooleanExtra("puretv_dark", prefs.getBoolean("dark", true))
        val next = Triple(top, bottom, dark)
        if (next == style.value && prefs.contains("dark")) return
        style.value = next
        prefs.edit().putInt("top", top).putInt("bottom", bottom).putBoolean("dark", dark).apply()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        _applyStyle(intent)
    }

    private fun _granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // PureTV: called by the service (main thread) when sharing has ended. On screen, the TV's home screen comes
    // forward; a picture-in-picture window only closes (the app under it stays), and so does a screen the viewer
    // already left, so nobody is pulled out of another app.
    private fun _leaveAfterSharing() {
        if (isFinishing || isDestroyed) return
        if (!isInPip.value && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            } catch (_: ActivityNotFoundException) {
                moveTaskToBack(true)
            } catch (_: SecurityException) {
                moveTaskToBack(true)
            }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun _autoStart() {
        if (LauncherControl.disabled(this)) return
        val svc = service ?: return
        if (viewModel.pref(Prefs.AUTO_START).value && svc.serverState.value == AirPlayService.ServerState.STOPPED && hasLocalNetwork()) {
            viewModel.startServer()
        }
    }
    
    private fun _refreshForeground() {
        if (service?.serverState?.value == AirPlayService.ServerState.RUNNING) {
            startService(Intent(this, AirPlayService::class.java))
        }
    }

    private fun _syncLocalNetwork(requested: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        val p = Manifest.permission.ACCESS_LOCAL_NETWORK
        val prev = viewModel.localNetwork.value
        val now = when {
            hasLocalNetwork() -> LocalNetworkAccess.GRANTED
            shouldShowRequestPermissionRationale(p) -> LocalNetworkAccess.ASK
            requested || prev == LocalNetworkAccess.SETTINGS -> LocalNetworkAccess.SETTINGS
            else -> LocalNetworkAccess.ASK
        }
        viewModel.setLocalNetwork(now)
        if (now == LocalNetworkAccess.GRANTED && prev != LocalNetworkAccess.GRANTED) _autoStart()
    }

    private fun _requestLocalNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
        if (viewModel.localNetwork.value == LocalNetworkAccess.SETTINGS) {
            startActivity(appSettingsIntent())
        } else {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK))
        }
    }

    override fun onStart() {
        super.onStart()
        _syncLocalNetwork()
        _refreshForeground()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        enterPictureInPictureMode(_pipParams())
    }

    private fun _pipParams(): PictureInPictureParams {
        val aspect = if (viewModel.videoPlaybackActive.value) viewModel.videoPlaybackAspect.value
            else viewModel.videoAspect.value
        val rational = Rational((aspect * 1000).toInt().coerceIn(1, 2390), 1000)
        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(_shouldAutoPip())
        }
        return builder.build()
    }

    private fun _shouldAutoPip(): Boolean =
        viewModel.serverState.value == AirPlayService.ServerState.RUNNING &&
            viewModel.connectionCount.value > 0

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && _shouldAutoPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        isInPip.value = inPip
    }

    override fun onStop() {
        super.onStop()
        // never stop mid-session
        if (!viewModel.pref(Prefs.RUN_IN_BACKGROUND).value && !isChangingConfigurations &&
            viewModel.serverState.value == AirPlayService.ServerState.RUNNING &&
            viewModel.connectionCount.value == 0) {
            viewModel.stopServer()
        }
    }

    override fun onDestroy() {
        service?.let {
            if (it.logCallback === logCallback) it.logCallback = null
            if (it.pinCallback === pinCallback) it.pinCallback = null
            if (it.sharingEndedCallback === sharingEnded) it.sharingEndedCallback = null
        }
        unbindService(connection)
        super.onDestroy()
    }
}
