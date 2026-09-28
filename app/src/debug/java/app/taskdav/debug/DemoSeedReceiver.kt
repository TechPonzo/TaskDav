package app.taskdav.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.taskdav.TaskDavApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DemoSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext as TaskDavApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DemoDataSeeder.seed(app)
                Log.i(TAG, "Demo data seeded")
            } catch (t: Throwable) {
                Log.e(TAG, "Demo seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "app.taskdav.debug.SEED_DEMO"
        private const val TAG = "DemoSeed"
    }
}
