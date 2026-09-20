package app.taskdav.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CollectionEntity::class,
        TaskEntity::class,
        EventEntity::class,
        NoteEntity::class,
        SyncMetaEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class TaskDavDatabase : RoomDatabase() {
    abstract fun collections(): CollectionDao
    abstract fun tasks(): TaskDao
    abstract fun events(): EventDao
    abstract fun notes(): NoteDao
    abstract fun syncMeta(): SyncMetaDao

    companion object {
        @Volatile
        private var instance: TaskDavDatabase? = null

        fun get(context: Context): TaskDavDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDavDatabase::class.java,
                    "taskdav.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
