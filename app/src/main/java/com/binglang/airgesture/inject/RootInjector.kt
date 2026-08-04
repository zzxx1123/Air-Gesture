package com.binglang.airgesture.inject

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.binglang.airgesture.Gesture
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Root 注入后端: libsu 常驻 shell, input 命令注入
 * shell UID 天生持有 INJECT_EVENTS, 延迟低于无障碍
 */
class RootInjector(private val ctx: Context) : Injector {

    override val name = "Root"

    override fun swipe(dir: Gesture) {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val cmd = when (dir) {
            Gesture.UP    -> "input swipe ${w/2} ${(h*0.72).toInt()} ${w/2} ${(h*0.28).toInt()} 250"
            Gesture.DOWN  -> "input swipe ${w/2} ${(h*0.28).toInt()} ${w/2} ${(h*0.72).toInt()} 250"
            Gesture.LEFT  -> "input swipe ${(w*0.78).toInt()} ${h/2} ${(w*0.22).toInt()} ${h/2} 250"
            Gesture.RIGHT -> "input swipe ${(w*0.22).toInt()} ${h/2} ${(w*0.78).toInt()} ${h/2} 250"
            Gesture.GRAB  -> return
        }
        Shell.cmd(cmd).submit()
    }

    override fun grab() {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "AirGesture"
        )
        dir.mkdirs()
        val name = "shot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val path = File(dir, name).absolutePath
        Shell.cmd("screencap -p '$path'").submit {
            MediaScannerConnection.scanFile(ctx, arrayOf(path), arrayOf("image/png"), null)
        }
    }
}
