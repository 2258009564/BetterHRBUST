package com.glassous.betterhrbust.data.repository

import com.glassous.betterhrbust.core.database.*
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.model.*
import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.core.network.CharsetDecoderHelper
import com.glassous.betterhrbust.core.parser.AcademicParsers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AcademicRepository(
    private val client: AcademicHttpClient,
    private val database: AppDatabase,
    private val prefs: UserPreferencesManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getTimetable(
        studentId: String,
        year: String,
        term: String,
        forceRefresh: Boolean = false
    ): Flow<Resource<TimetableResult>> = flow {
        emit(Resource.Loading)

        // 1. Check local cache
        val localEntity = database.timetableDao().getTimetable(studentId).firstOrNull()
        if (localEntity != null && !forceRefresh) {
            try {
                val cached = json.decodeFromString<TimetableResult>(localEntity.json)
                emit(Resource.Success(cached, isOfflineCache = true))
            } catch (_: Exception) {}
        }

        // 2. Fetch remote
        try {
            val url = "manager/coursearrange/showTimetable.do?id=$studentId&yearid=$year&termid=$term&timetableType=STUDENT&sectionType=COMBINE"
            val html = client.get(url, preferredCharset = CharsetDecoderHelper.GBK)
            val parsed = AcademicParsers.parseTimetable(html)

            // Cache to database
            val encoded = json.encodeToString(parsed)
            database.timetableDao().insert(TimetableEntity(studentId = studentId, json = encoded))

            emit(Resource.Success(parsed, isOfflineCache = false))
        } catch (e: Exception) {
            if (localEntity != null) {
                try {
                    val cached = json.decodeFromString<TimetableResult>(localEntity.json)
                    emit(Resource.Success(cached, isOfflineCache = true))
                    return@flow
                } catch (_: Exception) {}
            }
            emit(Resource.Error(e.message ?: "获取课表失败", e))
        }
    }

    fun getScores(studentId: String, forceRefresh: Boolean = false): Flow<Resource<ScoreResult>> = flow {
        emit(Resource.Loading)

        val localEntity = database.scoreDao().getScores(studentId).firstOrNull()
        if (localEntity != null && !forceRefresh) {
            try {
                val cached = json.decodeFromString<ScoreResult>(localEntity.json)
                emit(Resource.Success(cached, isOfflineCache = true))
            } catch (_: Exception) {}
        }

        try {
            val html = client.post(
                "manager/score/studentOwnScore.do",
                formBody = mapOf("para" to "0"),
                preferredCharset = CharsetDecoderHelper.UTF_8
            )
            val parsed = AcademicParsers.parseScores(html)

            val encoded = json.encodeToString(parsed)
            database.scoreDao().insert(ScoreEntity(studentId = studentId, json = encoded))

            emit(Resource.Success(parsed, isOfflineCache = false))
        } catch (e: Exception) {
            if (localEntity != null) {
                try {
                    val cached = json.decodeFromString<ScoreResult>(localEntity.json)
                    emit(Resource.Success(cached, isOfflineCache = true))
                    return@flow
                } catch (_: Exception) {}
            }
            emit(Resource.Error(e.message ?: "获取成绩失败", e))
        }
    }

    fun getExams(studentId: String, forceRefresh: Boolean = false): Flow<Resource<List<ExamItem>>> = flow {
        emit(Resource.Loading)

        val localEntity = database.examDao().getExams(studentId).firstOrNull()
        if (localEntity != null && !forceRefresh) {
            try {
                val cached = json.decodeFromString<List<ExamItem>>(localEntity.json)
                emit(Resource.Success(cached, isOfflineCache = true))
            } catch (_: Exception) {}
        }

        try {
            val html = client.get(
                "manager/examstu/studentQueryAllExam.do?pagingNumberPerVLID=100",
                preferredCharset = CharsetDecoderHelper.UTF_8
            )
            val parsed = AcademicParsers.parseExams(html)

            val encoded = json.encodeToString(parsed)
            database.examDao().insert(ExamEntity(studentId = studentId, json = encoded))

            emit(Resource.Success(parsed, isOfflineCache = false))
        } catch (e: Exception) {
            if (localEntity != null) {
                try {
                    val cached = json.decodeFromString<List<ExamItem>>(localEntity.json)
                    emit(Resource.Success(cached, isOfflineCache = true))
                    return@flow
                } catch (_: Exception) {}
            }
            emit(Resource.Error(e.message ?: "获取考试安排失败", e))
        }
    }

    fun getPersonalInfo(studentNumber: String, forceRefresh: Boolean = false): Flow<Resource<PersonalInfo>> = flow {
        emit(Resource.Loading)

        val localEntity = database.profileDao().getProfile(studentNumber).firstOrNull()
        if (localEntity != null && !forceRefresh) {
            try {
                val cached = json.decodeFromString<PersonalInfo>(localEntity.json)
                emit(Resource.Success(cached, isOfflineCache = true))
            } catch (_: Exception) {}
        }

        try {
            val html = client.get("showPersonalInfo.do", preferredCharset = CharsetDecoderHelper.UTF_8)
            val parsed = AcademicParsers.parsePersonalInfo(html)

            val sNumber = parsed.studentNumber.ifEmpty { studentNumber }
            val encoded = json.encodeToString(parsed)
            database.profileDao().insert(ProfileEntity(studentNumber = sNumber, json = encoded))

            emit(Resource.Success(parsed, isOfflineCache = false))
        } catch (e: Exception) {
            if (localEntity != null) {
                try {
                    val cached = json.decodeFromString<PersonalInfo>(localEntity.json)
                    emit(Resource.Success(cached, isOfflineCache = true))
                    return@flow
                } catch (_: Exception) {}
            }
            emit(Resource.Error(e.message ?: "获取个人档案失败", e))
        }
    }

    fun getCurriculumPlan(studentId: String, forceRefresh: Boolean = false): Flow<Resource<CurriculumPlanResult>> = flow {
        emit(Resource.Loading)

        val localEntity = database.curriculumDao().getPlan(studentId).firstOrNull()
        if (localEntity != null && !forceRefresh) {
            try {
                val cached = json.decodeFromString<CurriculumPlanResult>(localEntity.json)
                emit(Resource.Success(cached, isOfflineCache = true))
            } catch (_: Exception) {}
        }

        try {
            // Step 1: Query studentSelfSchedule.jsdo to extract encrypted studentId
            val jumpHtml = client.get("manager/studyschedule/studentSelfSchedule.jsdo", preferredCharset = CharsetDecoderHelper.GBK)
            val encIdMatch = Regex("""studentId=([^&"']+)""").find(jumpHtml)
            val encStudentId = encIdMatch?.groupValues?.get(1) ?: studentId

            // Step 2: Query studentScheduleShowByTerm.do
            val planHtml = client.get(
                "manager/studyschedule/studentScheduleShowByTerm.do?z=z&studentId=$encStudentId",
                preferredCharset = CharsetDecoderHelper.GBK
            )
            val parsed = AcademicParsers.parseCurriculumPlan(planHtml)

            val encoded = json.encodeToString(parsed)
            database.curriculumDao().insert(CurriculumPlanEntity(studentId = studentId, json = encoded))

            emit(Resource.Success(parsed, isOfflineCache = false))
        } catch (e: Exception) {
            if (localEntity != null) {
                try {
                    val cached = json.decodeFromString<CurriculumPlanResult>(localEntity.json)
                    emit(Resource.Success(cached, isOfflineCache = true))
                    return@flow
                } catch (_: Exception) {}
            }
            emit(Resource.Error(e.message ?: "获取培养方案失败", e))
        }
    }

    fun getClassroomQueryOptions(): Flow<Resource<ClassroomQueryOptions>> = flow {
        emit(Resource.Loading)
        try {
            val html = client.get("teacher/teachresource/roomschedulequery.jsdo", preferredCharset = CharsetDecoderHelper.GBK)
            val options = AcademicParsers.parseClassroomQueryOptions(html)
            emit(Resource.Success(options))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "获取教室选项失败", e))
        }
    }

    fun searchCourses(keyword: String): Flow<Resource<List<CourseSearchItem>>> = flow {
        emit(Resource.Loading)
        try {
            val form = mapOf(
                "keyvalue" to keyword,
                "depid" to "1",
                "terms" to "",
                "status" to ""
            )
            val html = client.post(
                "manager/querycourse/course_list.jsdo",
                formBody = form,
                preferredCharset = CharsetDecoderHelper.GBK,
                encodeFormWithGbk = true
            )
            val list = AcademicParsers.parseCourseList(html)
            emit(Resource.Success(list))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "课程检索失败", e))
        }
    }

    fun getNotices(forceRefresh: Boolean = false): Flow<Resource<List<NoticeItem>>> = flow {
        emit(Resource.Loading)

        val localEntities = database.noticeDao().getNotices().firstOrNull()
        if (!localEntities.isNullOrEmpty() && !forceRefresh) {
            val list = localEntities.map { NoticeItem(id = it.id, title = it.title, content = it.content, date = it.date) }
            emit(Resource.Success(list, isOfflineCache = true))
        }

        try {
            val html = client.get("calendarinfo/viewCalendarInfo.do", preferredCharset = CharsetDecoderHelper.UTF_8)
            val calendarInfo = AcademicParsers.parseCalendarInfo(html)

            val entities = calendarInfo.notices.map {
                NoticeEntity(id = it.id, title = it.title, content = it.content, date = it.date)
            }
            database.noticeDao().insertAll(entities)

            prefs.setCurrentWeek(calendarInfo.currentWeek)

            emit(Resource.Success(calendarInfo.notices, isOfflineCache = false))
        } catch (e: Exception) {
            if (!localEntities.isNullOrEmpty()) {
                val list = localEntities.map { NoticeItem(id = it.id, title = it.title, content = it.content, date = it.date) }
                emit(Resource.Success(list, isOfflineCache = true))
                return@flow
            }
            emit(Resource.Error(e.message ?: "获取教学公告失败", e))
        }
    }

    fun getTeachingWeek(): Flow<Int> = flow {
        try {
            val html = client.get("listLeft.do", preferredCharset = CharsetDecoderHelper.GBK)
            val week = AcademicParsers.parseTeachingWeek(html)
            prefs.setCurrentWeek(week)
            emit(week)
        } catch (_: Exception) {
            emit(1)
        }
    }
}
