package app.taskdav.calendar

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TaskDavAuthenticatorService : Service() {
    private lateinit var authenticator: TaskDavAuthenticator

    override fun onCreate() {
        authenticator = TaskDavAuthenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}
