package com.binglang.airgesture

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // —— 权限 ——
        val need = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val notGranted = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) permLauncher.launch(notGranted.toTypedArray())

        // —— 总开关 ——
        val sw = findViewById<Switch>(R.id.sw_enable)
        sw.isChecked = Prefs.isEnabled(this)
        sw.setOnCheckedChangeListener { _, on ->
            Prefs.setEnabled(this, on)
            val i = Intent(this, GestureService::class.java)
                .setAction(if (on) GestureService.ACTION_START else GestureService.ACTION_STOP)
            if (on) ContextCompat.startForegroundService(this, i) else startService(i)
            refreshStatus()
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_root).setOnClickListener {
            Thread { Shell.getShell(); runOnUiThread { refreshStatus() } }.start()
        }

        // —— 复选框 ——
        bindCheck(R.id.cb_root, "rootPreferred")
        bindCheck(R.id.cb_vibrate, "vibrate")
        bindCheck(R.id.cb_mirrorx, "mirrorX")
        bindCheck(R.id.cb_mirrory, "mirrorY")

        // —— 磨参滑杆 ——
        bindSlider(R.id.sb_hold, R.id.tv_hold, "holdMs", 500, "起手式停顿", "ms")
        bindSlider(R.id.sb_swipe, R.id.tv_swipe, "swipeMin", 12, "挥动幅度阈值", "%")
        bindSlider(R.id.sb_grab, R.id.tv_grab, "grabDrop", 45, "握拳灵敏度", "%")
        bindSlider(R.id.sb_scalemin, R.id.tv_scalemin, "scaleMin", 8, "距离下限", "%")
        bindSlider(R.id.sb_scalemax, R.id.tv_scalemax, "scaleMax", 28, "距离上限", "%")
        bindSlider(R.id.sb_cooldown, R.id.tv_cooldown, "cooldownMs", 800, "触发冷却", "ms")
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun bindCheck(id: Int, key: String) {
        val cb = findViewById<CheckBox>(id)
        cb.isChecked = Prefs.sp(this).getBoolean(key, true)
        cb.setOnCheckedChangeListener { _, v ->
            Prefs.sp(this).edit().putBoolean(key, v).apply()
        }
    }

    private fun bindSlider(barId: Int, labelId: Int, key: String, def: Int, name: String, unit: String) {
        val bar = findViewById<SeekBar>(barId)
        val label = findViewById<TextView>(labelId)
        fun show(v: Int) { label.text = "$name: $v$unit" }
        bar.progress = Prefs.sp(this).getInt(key, def)
        show(bar.progress)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) = show(p)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                Prefs.sp(this@MainActivity).edit().putInt(key, bar.progress).apply()
            }
        })
    }

    private fun refreshStatus() {
        val root = when (Shell.isAppGrantedRoot()) {
            true -> "已授权"; false -> "被拒绝"; null -> "未询问"
        }
        val acc = isAccessibilityOn()
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        findViewById<TextView>(R.id.tv_status).text = buildString {
            append("相机权限: ").append(if (cam) "✓" else "✗")
            append("\nRoot: ").append(root)
            append("\n无障碍服务: ").append(if (acc) "已启用" else "未启用(无 Root 时必须)")
            append("\n服务: ").append(if (GestureService.isRunning) "运行中" else "停止")
            append("\n注入后端: ").append(GestureService.backendName)
        }
    }

    private fun isAccessibilityOn(): Boolean {
        val me = ComponentName(this, com.binglang.airgesture.inject.GestureBridge::class.java)
            .flattenToString()
        val s = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.equals(me, ignoreCase = true) }
    }
}