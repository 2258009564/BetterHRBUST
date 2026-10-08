package com.glassous.betterhrbust.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(
    val username: String = "",
    val password: String = ""
)

@Serializable
sealed interface AuthState {
    @Serializable
    data object Unauthenticated : AuthState

    @Serializable
    data class Authenticated(
        val username: String,
        val studentId: String
    ) : AuthState

    @Serializable
    data class Error(
        val message: String
    ) : AuthState
}

@Serializable
data class StudentContext(
    val studentId: String,
    val year: String,
    val term: String,
    val courses: List<CurrentCourseItem> = emptyList()
)

@Serializable
data class CurrentCourseItem(
    val courseId: String,
    val courseSeq: String,
    val courseName: String,
    val teacher: String,
    val credit: Double,
    val property: String,
    val examWay: String,
    val examType: String,
    val timeAndPlace: String
)

@Serializable
data class PersonalInfo(
    val studentNumber: String = "",
    val realName: String = "",
    val college: String = "",
    val major: String = "",
    val direction: String = "",
    val studentType: String = "",
    val grade: String = "",
    val className: String = "",
    val idCard: String = "",
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val postalCode: String = "",
    val status: String = "在籍",
    val photoUrl: String = "",
    val changes: List<StatusChange> = emptyList()
)

@Serializable
data class StatusChange(
    val type: String,
    val date: String,
    val reason: String,
    val remark: String
)

@Serializable
data class TimetableResult(
    val cells: List<TimetableCell> = emptyList(),
    val unarranged: List<UnarrangedCourse> = emptyList()
)

@Serializable
data class TimetableCell(
    val id: String,
    val courseId: String,
    val day: Int,           // 1..7 (周一至周日)
    val sectionIndex: Int,  // 1..6 大节
    val sectionLabel: String,
    val courseName: String,
    val courseSeq: String,
    val location: String,
    val teacher: String,
    val weeks: String,
    val hoursType: String,
    val rawLines: List<String> = emptyList()
)

@Serializable
data class UnarrangedCourse(
    val courseId: String,
    val courseName: String,
    val courseSeq: String,
    val teacher: String,
    val mergeClass: String,
    val weeks: String,
    val day: String,
    val location: String
)

@Serializable
data class ScoreResult(
    val scores: List<ScoreItem> = emptyList(),
    val yearOptions: List<YearOption> = emptyList()
)

@Serializable
data class ScoreItem(
    val year: String,
    val term: String,
    val courseId: String,
    val courseName: String,
    val courseSeq: String,
    val courseGroup: String,
    val score: String,
    val credit: Double,
    val hours: Int,
    val property: String,
    val remark: String,
    val examType: String,
    val passMark: String,
    val passed: Boolean
)

@Serializable
data class YearOption(
    val value: String,
    val label: String
)

@Serializable
data class ScoreStats(
    val gpa: Double,
    val weightedAvg: Double,
    val totalCredits: Double,
    val earnedCredits: Double,
    val failedCount: Int,
    val excellentRate: Double
)

@Serializable
data class ExamItem(
    val courseId: String,
    val courseName: String,
    val time: String,
    val location: String,
    val property: String,
    val countdownDays: Int? = null,
    val isUpcoming: Boolean = true
)

@Serializable
data class CurriculumPlanResult(
    val groups: List<CurriculumGroup> = emptyList()
)

@Serializable
data class CurriculumGroup(
    val id: String,
    val name: String,
    val property: String,
    val requiredCredits: Double,
    val requiredCourses: Int,
    val earnedCredits: Double = 0.0,
    val passedCourses: Int = 0,
    val courses: List<PlanCourseDetail> = emptyList()
)

@Serializable
data class PlanCourseDetail(
    val code: String,
    val name: String,
    val credit: Double,
    val hours: Int,
    val property: String
)

@Serializable
data class ClassroomQueryOptions(
    val areas: List<NamedOption> = emptyList(),
    val buildings: List<NamedOption> = emptyList(),
    val rooms: List<NamedOption> = emptyList()
)

@Serializable
data class NamedOption(
    val id: String,
    val name: String
)

@Serializable
data class FreeClassroomSlot(
    val roomName: String,
    val buildingName: String,
    val day: Int,
    val startSection: Int,
    val endSection: Int
)

@Serializable
data class CourseSearchItem(
    val courseId: String,
    val courseName: String,
    val credit: Double,
    val hours: Int,
    val department: String,
    val examWay: String = "",
    val teacher: String = "",
    val isCurrent: Boolean = false
)

@Serializable
data class CalendarInfo(
    val currentWeek: Int = 1,
    val semesterName: String = "",
    val notices: List<NoticeItem> = emptyList()
)

@Serializable
data class NoticeItem(
    val id: String = "",
    val title: String,
    val content: String,
    val date: String
)
