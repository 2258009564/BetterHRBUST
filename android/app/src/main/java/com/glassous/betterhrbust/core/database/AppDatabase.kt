package com.glassous.betterhrbust.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TimetableEntity::class,
        ScoreEntity::class,
        ExamEntity::class,
        ProfileEntity::class,
        NoticeEntity::class,
        CurriculumPlanEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun scoreDao(): ScoreDao
    abstract fun examDao(): ExamDao
    abstract fun profileDao(): ProfileDao
    abstract fun noticeDao(): NoticeDao
    abstract fun curriculumDao(): CurriculumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "better_hrbust.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
