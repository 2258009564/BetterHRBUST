package com.glassous.betterhrbust.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_cache WHERE studentId = :studentId")
    fun getTimetable(studentId: String): Flow<TimetableEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TimetableEntity)

    @Query("DELETE FROM timetable_cache WHERE studentId = :studentId")
    suspend fun clear(studentId: String)
}

@Dao
interface ScoreDao {
    @Query("SELECT * FROM score_cache WHERE studentId = :studentId")
    fun getScores(studentId: String): Flow<ScoreEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScoreEntity)

    @Query("DELETE FROM score_cache WHERE studentId = :studentId")
    suspend fun clear(studentId: String)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exam_cache WHERE studentId = :studentId")
    fun getExams(studentId: String): Flow<ExamEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExamEntity)

    @Query("DELETE FROM exam_cache WHERE studentId = :studentId")
    suspend fun clear(studentId: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile_cache WHERE studentNumber = :studentNumber")
    fun getProfile(studentNumber: String): Flow<ProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ProfileEntity)

    @Query("DELETE FROM profile_cache")
    suspend fun clearAll()
}

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notice_cache ORDER BY date DESC")
    fun getNotices(): Flow<List<NoticeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<NoticeEntity>)

    @Query("UPDATE notice_cache SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("DELETE FROM notice_cache")
    suspend fun clearAll()
}

@Dao
interface CurriculumDao {
    @Query("SELECT * FROM curriculum_cache WHERE studentId = :studentId")
    fun getPlan(studentId: String): Flow<CurriculumPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CurriculumPlanEntity)

    @Query("DELETE FROM curriculum_cache WHERE studentId = :studentId")
    suspend fun clear(studentId: String)
}
