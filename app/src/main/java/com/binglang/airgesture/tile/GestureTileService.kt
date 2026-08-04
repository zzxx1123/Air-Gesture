package com.binglang.airgesture.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.binglang.airgesture.GestureService
import com.binglang.airgesture.MainActivity
import com.binglang.airgesture.Prefs

/** 快捷设置磁贴 = 手动保险总开关 */
class GestureTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (Prefs.isEnabled(this@GestureTileService) && GestureService.isRunning)
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val ctx = this
        if (Prefs.isEnabled(ctx) && GestureService.isRunning) {
            Prefs.setEnabled(ctx, false)
            startService(Intent(ctx, GestureService::class.java).setAction(GestureService.ACTION_STOP))
        } else {
            Prefs.setEnabled(ctx, true)
            try {
                ContextCompat.startForegroundService(
                    ctx, Intent(ctx, GestureService::class.java).setAction(GestureService.ACTION_START))
            } catch (t: Throwable) {
                // 部分系统限制后台起 FGS: 退回主界面手动开
                startActivityAndCollapse(
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        onStartListening()
    }
}
