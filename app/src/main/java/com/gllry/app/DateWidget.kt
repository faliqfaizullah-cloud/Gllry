package com.gllry.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 2x2 widget, 28dp rounded, photo + date typography (light sans + italic serif) + glass pill. */
class DateWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle?) {
        update(ctx, mgr, id)
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, DateWidget::class.java)).forEach { update(ctx, mgr, it) }
        }

        private fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val o = mgr.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(100)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(100)
            val views = RemoteViews(ctx.packageName, R.layout.widget_date)
            views.setImageViewBitmap(R.id.wImg, render(ctx, wDp, hDp))
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.wImg, pi)
            mgr.updateAppWidget(id, views)
        }

        private fun latestPhoto(ctx: Context, w: Int, h: Int): Bitmap? = runCatching {
            val hidden = Store.archived(ctx)
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    if (id.toString() in hidden) continue
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    return@runCatching ctx.contentResolver.loadThumbnail(uri, Size(w, h), null)
                }
                null
            }
        }.getOrNull()

        private fun render(ctx: Context, wDp: Int, hDp: Int): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val w = (wDp * d).toInt(); val h = (hDp * d).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = 28f * d                                    // 28dp corners
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            c.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })

            val src = latestPhoto(ctx, w, h)
            if (src != null) {
                val s = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val m = Matrix().apply {
                    postScale(s, s)
                    postTranslate((w - src.width * s) / 2f, (h - src.height * s) / 2f)
                }
                c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                c.drawRect(rect, Paint().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF5B8DB8.toInt(), 0xFF1F3FE0.toInt(), Shader.TileMode.CLAMP)
                })
            }
            c.drawRect(rect, Paint().apply {
                shader = LinearGradient(0f, h * 0.30f, 0f, h.toFloat(), 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP)
            })

            // date typography: "Wed" (light sans) + " 7." (italic serif)
            val today = LocalDate.now()
            val day = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val num = " ${today.dayOfMonth}."
            val sans = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
            val serif = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
            var size = 28f * d
            while (true) {
                sans.textSize = size; serif.textSize = size
                if (sans.measureText(day) + serif.measureText(num) <= w * 0.84f) break
                size *= 0.94f
            }
            val total = sans.measureText(day) + serif.measureText(num)
            val pillH = 26f * d; val pillW = 78f * d
            val pillTop = h - 14f * d - pillH
            val subBase = pillTop - 8f * d
            val mainBase = subBase - 17f * d
            val x0 = (w - total) / 2f
            c.drawText(day, x0, mainBase, sans)
            c.drawText(num, x0 + sans.measureText(day), mainBase, serif)

            val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt(); textSize = 9.5f * d; textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            c.drawText("${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.year}", w / 2f, subBase, sub)

            val pill = RectF((w - pillW) / 2f, pillTop, (w + pillW) / 2f, pillTop + pillH)
            c.drawRoundRect(pill, pillH / 2, pillH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, pill.top, 0f, pill.bottom, 0xFFFFFFFF.toInt(), 0xFFC9C9C9.toInt(), Shader.TileMode.CLAMP)
            })
            val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF111111.toInt(); textSize = 11f * d; textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            c.drawText("Gllry", w / 2f, pill.centerY() + 4f * d, pt)

            // white bezel rim like the reference
            c.drawRoundRect(
                RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d), r - 1.5f * d, r - 1.5f * d,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * d; color = 0xE6FFFFFF.toInt() }
            )
            return bmp
        }
    }
}
