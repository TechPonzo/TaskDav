package app.taskdav.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

fun openLocationInMaps(context: Context, location: String) {
    val query = location.trim()
    if (query.isEmpty()) return
    val geo = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
    val web = Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, web))
        } catch (_: ActivityNotFoundException) {
            // no map app available
        }
    }
}
