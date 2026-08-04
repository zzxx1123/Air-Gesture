package com.binglang.airgesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.binglang.airgesture.engine.HandGestureEngine
import com.binglang.airgesture.engine.HuaweiGestureStateMachine
import com.binglang.airgesture.inject.AccessibilityInjector
import com.binglang.airgesture.inject.GestureBridge
import com.binglang.airgesture.inject.Injector
import com.binglang.airgesture.inject.RootInjector
import com.topjohnwu.superuser.Shell
import java.util.concurrent.Executors

/**
 * 运行时总装: 前摄 -> 感知引擎 -> 华为状态机 -> 注入后端
 * 前台服务(类型 camera), 息屏自动停相机, 亮屏恢复
 */
class GestureService : LifecycleService() {

    companion object {
        const val ACTION_START = "com.binglang.airgesture.START"
        const val ACTION_STOP  = "com.binglang.airgesture.STOP"
        @Volatile var isRunning = false
        @Volatile var backendName = "-"
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var engine: HandGestureEngine? = null
    private var machine: HuaweiGestureStateMachine? = null
    private var cameraBound = false
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> unbindCamera()           // 省电: 息屏不感知
                Intent.ACTION_SCREEN_ON  -> if (Prefs.isEnabled(this@GestureService)) bindCamera()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = NotificationManagerCompat.from(this)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("gesture", NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.channel_name)).build()
        )
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            else -> if (!isRunning) start()
        }
        return START_STICKY
    }

    private fun start() {
        val injector = pickInjector()
        if (injector == null) {
            notify(getString(R.string.notif_running, "无可用后端,请开无障碍或 Root"), null)
            shutdown(); return
        }
        backendName = injector.name
        notify(getString(R.string.notif_running, injector.name), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)

        machine = HuaweiGestureStateMachine(
            injector = injector,
            vibrate = { ms ->
                vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            },
            config = { Prefs.load(this) }
        )
        engine = HandGestureEngine(this) { s -> machine?.onFrame(s) }
        isRunning = true
        bindCamera()
    }

    /** 后端选择: 优先 Root(已授权时), 否则无障碍; 无障碍 + Root 混合(截图回落) */
    private fun pickInjector(): Injector? {
        val c = Prefs.load(this)
        val rootGranted = c.rootPreferred && Shell.isAppGrantedRoot() == true
        if (rootGranted) return RootInjector(this)
        if (GestureBridge.instance != null) {
            val rootFallback = if (Shell.isAppGrantedRoot() == true) RootInjector(this) else null
            return AccessibilityInjector(rootFallback)
        }
        return null
    }

    private fun bindCamera() {
        if (cameraBound) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(320, 240),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                engine?.let { e -> analysis.setAnalyzer(analysisExecutor, e) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                cameraBound = true
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindCamera() {
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
        } catch (_: Throwable) {}
        cameraBound = false
    }

    private fun notify(text: String, fgsType: Int?) {
        val n = NotificationCompat.Builder(this, "gesture")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
        if (fgsType != null && Build.VERSION.SDK_INT >= 29)
            ServiceCompat.startForeground(this, 1, n, fgsType)
        else
            startForeground(1, n)
    }

    private fun shutdown() {
        isRunning = false
        unbindCamera()
        engine?.close(); engine = null
        machine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        try { unregisterReceiver(screenReceiver) } catch (_: Throwable) {}
        analysisExecutor.shutdown()
        super.onDestroy()
    }
}
