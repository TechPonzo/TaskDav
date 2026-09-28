package app.taskdav.debug

import app.taskdav.TaskDavApp
import app.taskdav.caldav.IcalMapper
import app.taskdav.data.LanguagePreference
import app.taskdav.data.LocaleHelper
import app.taskdav.data.SyncBackend
import app.taskdav.domain.TaskEditorState
import java.util.Calendar

/**
 * Fills a fresh local-only workspace with generic sample data for marketing
 * screenshots. Debug builds only — trigger with:
 *
 * ```
 * adb shell am broadcast -n app.taskdav/.debug.DemoSeedReceiver -a app.taskdav.debug.SEED_DEMO
 * ```
 */
object DemoDataSeeder {
    suspend fun seed(app: TaskDavApp) {
        LocaleHelper.persist(app, LanguagePreference.ENGLISH)
        app.repository.setSyncBackend(SyncBackend.LOCAL)
        app.notifySyncBackendChanged()

        // Wipe prior demo / race-created rows so screenshots stay predictable.
        for (col in app.database.collections().getAll()) {
            app.database.tasks().deleteAllForCollection(col.id)
            app.database.events().deleteAllForCollection(col.id)
            app.database.notes().deleteAllForCollection(col.id)
        }
        app.database.collections().deleteAll()
        val collectionId = app.repository.ensureLocalWorkspace()

        app.appearanceStore.setCalendarViewMode("monthly_daily")
        app.appearanceStore.setSeedColorArgb(0xFF1F6B4A.toInt())

        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        fun at(dayOffset: Int, hour: Int, minute: Int = 0): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        val releaseUid = IcalMapper.newUid()
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = releaseUid,
                collectionId = collectionId,
                summary = "Release 1.0",
                description = "Ship the first public build",
                isCategory = true,
                categories = "shipping",
            ),
        )

        val talkUid = IcalMapper.newUid()
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = talkUid,
                collectionId = collectionId,
                summary = "Prepare conference talk",
                description = "CalDAV for everyday planning",
                dueMillis = at(5, 18, 0),
                categories = "speaking",
                priority = 1,
            ),
        )

        val householdUid = IcalMapper.newUid()
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = householdUid,
                collectionId = collectionId,
                summary = "Household",
                isCategory = true,
                categories = "life",
            ),
        )

        val critiqueUid = app.repository.createEvent(
            collectionId = collectionId,
            summary = "Design critique",
            startMillis = at(0, 15, 0),
            endMillis = at(0, 16, 0),
            location = "Studio",
            description = "Review landing page mockups",
        )

        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Polish README screenshots",
                description = "Home, calendar, tasks, and notes",
                dueMillis = at(0, 17, 0),
                parentUid = releaseUid,
                linkedEventUid = critiqueUid,
                categories = "docs",
                priority = 1,
            ),
        )
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Write F-Droid metadata",
                description = "Short description, anti-features, screenshots",
                dueMillis = at(2, 12, 0),
                parentUid = releaseUid,
                categories = "shipping",
            ),
        )
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Triage open issues",
                dueMillis = at(-1, 18, 0),
                parentUid = releaseUid,
                categories = "maintenance",
                status = "IN-PROCESS",
                percentComplete = 40,
            ),
        )

        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Outline slides",
                parentUid = talkUid,
                dueMillis = at(1, 20, 0),
            ),
        )
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Practice timing",
                parentUid = talkUid,
                dueMillis = at(4, 20, 0),
            ),
        )

        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Order coffee beans",
                parentUid = householdUid,
                dueMillis = at(3, 12, 0),
                categories = "errands",
            ),
        )
        app.repository.createOrUpdateTask(
            TaskEditorState(
                uid = IcalMapper.newUid(),
                collectionId = collectionId,
                summary = "Water the plants",
                parentUid = householdUid,
                dueMillis = at(0, 9, 0),
            ),
        )

        app.repository.createEvent(
            collectionId = collectionId,
            summary = "Deep work",
            startMillis = at(0, 9, 0),
            endMillis = at(0, 11, 30),
            location = "Home office",
            description = "No meetings — sync engine polish",
        )
        app.repository.createEvent(
            collectionId = collectionId,
            summary = "Lunch walk",
            startMillis = at(0, 12, 30),
            endMillis = at(0, 13, 15),
            location = "Park",
        )
        app.repository.createEvent(
            collectionId = collectionId,
            summary = "Weekly sync",
            startMillis = at(1, 10, 0),
            endMillis = at(1, 10, 30),
            description = "Status with the maintainers",
            rrule = "FREQ=WEEKLY;INTERVAL=1",
        )
        app.repository.createEvent(
            collectionId = collectionId,
            summary = "Open-source office hours",
            startMillis = at(3, 18, 0),
            endMillis = at(3, 19, 0),
            location = "Jitsi",
            description = "Help newcomers with CalDAV setup",
        )
        app.repository.createEvent(
            collectionId = collectionId,
            summary = "Farmers market",
            startMillis = at(5, 9, 0),
            endMillis = at(5, 11, 0),
            location = "Town square",
        )

        app.repository.createOrUpdateNote(
            id = null,
            uid = IcalMapper.newUid(),
            collectionId = collectionId,
            summary = "Architecture notes",
            description = """
                Sync strategy
                • Local Room first, push when online
                • Prefer newer Last-Modified on conflict
                • Keep DAVx⁵ for contacts if needed

                Next experiments
                • Occurrence exceptions for RRULE
                • Shared collections / invitations
            """.trimIndent(),
            categories = "engineering",
        )
        app.repository.createOrUpdateNote(
            id = null,
            uid = IcalMapper.newUid(),
            collectionId = collectionId,
            summary = "Packing list",
            description = "Laptop · USB-C hub · Badge · Headphones · Water bottle",
            categories = "travel",
        )
        app.repository.createOrUpdateNote(
            id = null,
            uid = IcalMapper.newUid(),
            collectionId = collectionId,
            summary = "Ideas for v1.1",
            description = "Widget dark theme · ICS import · Natural-language quick add",
            categories = "product",
        )

        app.onboardingStore.setCompleted(true)
        app.notifyWidgetsChanged()
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
