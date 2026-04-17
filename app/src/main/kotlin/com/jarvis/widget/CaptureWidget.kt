package com.jarvis.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jarvis.MainActivity
import com.jarvis.R

/**
 * Home-screen widget: single button that deep-links into MainActivity with the
 * CAPTURE action so the app opens directly on the capture view and starts
 * recording immediately.
 */
class CaptureWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.jarvis.action.CAPTURE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_capture).apply {
            setOnClickPendingIntent(R.id.widget_root, pi)
        }
        manager.updateAppWidget(ids, views)
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, CaptureWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, CaptureWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
