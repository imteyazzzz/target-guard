package com.remoteappblocker.target.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.remoteappblocker.target.R

object OverlayBlockManager {

    private const val TAG = "OverlayBlockManager"
    private var overlayView: View? = null

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun showOverlay(context: Context, appName: String, message: String) {
        if (!isOverlayPermissionGranted(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        dismissOverlay(context)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_block_layout, null)
            val titleTv = view.findViewById<TextView>(R.id.tvOverlayAppName)
            val msgTv = view.findViewById<TextView>(R.id.tvOverlayMessage)
            val homeBtn = view.findViewById<Button>(R.id.btnOverlayHome)

            titleTv?.text = appName
            msgTv?.text = message

            homeBtn?.setOnClickListener {
                dismissOverlay(context)
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }

            windowManager.addView(view, params)
            overlayView = view
            Log.i(TAG, "Fallback block overlay displayed for $appName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show block overlay", e)
        }
    }

    fun dismissOverlay(context: Context) {
        if (overlayView != null) {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(overlayView)
                Log.d(TAG, "Block overlay dismissed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            } finally {
                overlayView = null
            }
        }
    }
}
