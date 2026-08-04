package com.tradepluss.widget

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TypefaceSpan
import android.widget.RemoteViews
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies Vazirmatn to RemoteViews TextViews.
 * XML fontFamily is often ignored by launcher RemoteViews (especially Samsung);
 * TypefaceSpan is the reliable path on API 28+ (Galaxy A34 = API 34).
 */
object FontHelper {

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun regular(context: Context): Typeface = load(context, "fonts/vazirmatn_regular.ttf")
    fun medium(context: Context): Typeface = load(context, "fonts/vazirmatn_medium.ttf")
    fun bold(context: Context): Typeface = load(context, "fonts/vazirmatn_bold.ttf")

    private fun load(context: Context, path: String): Typeface {
        cache[path]?.let { return it }
        val tf = try {
            Typeface.createFromAsset(context.applicationContext.assets, path)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        cache[path] = tf
        return tf
    }

    fun setText(views: RemoteViews, context: Context, viewId: Int, text: CharSequence, bold: Boolean = false) {
        val plain = text.toString()
        val tf = if (bold) bold(context) else regular(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val span = SpannableString(plain)
            span.setSpan(
                TypefaceSpan(tf),
                0,
                plain.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            views.setTextViewText(viewId, span)
        } else {
            views.setTextViewText(viewId, plain)
        }
    }
}
