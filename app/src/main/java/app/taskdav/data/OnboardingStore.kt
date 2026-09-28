package app.taskdav.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * First-launch onboarding. Existing installs are migrated as already completed
 * when any collection already exists.
 *
 * [completed] is null until [migrateIfNeeded] finishes.
 */
class OnboardingStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _completed = MutableStateFlow<Boolean?>(null)
    val completed: StateFlow<Boolean?> = _completed.asStateFlow()

    fun setCompleted(done: Boolean) {
        prefs.edit().putBoolean(KEY_DONE, done).apply()
        _completed.value = done
    }

    /**
     * Call once at startup. If the flag was never written, treat returning users
     * (who already have collections) as onboarded.
     */
    suspend fun migrateIfNeeded(database: TaskDavDatabase) = withContext(Dispatchers.IO) {
        if (prefs.contains(KEY_DONE)) {
            _completed.value = prefs.getBoolean(KEY_DONE, false)
            return@withContext
        }
        val hasCollections = database.collections().getAll().isNotEmpty()
        setCompleted(hasCollections)
    }

    companion object {
        private const val PREFS = "taskdav_onboarding"
        private const val KEY_DONE = "onboarding_completed"
    }
}
