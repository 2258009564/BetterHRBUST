package com.glassous.betterhrbust

import com.glassous.betterhrbust.data.repository.Resource
import com.glassous.betterhrbust.data.sync.AcademicSyncManager
import org.junit.Assert.*
import org.junit.Test

class SyncVerificationTest {
    @Test fun onlyFreshResultsCountAsCompleteSync() {
        assertTrue(AcademicSyncManager.wasFullyRefreshed(listOf(Resource.Success("课表"), Resource.Success(emptyList<String>()), 6)))
        assertFalse(AcademicSyncManager.wasFullyRefreshed(listOf(Resource.Success("旧课表", isOfflineCache = true), Resource.Success("成绩"), 6)))
        assertFalse(AcademicSyncManager.wasFullyRefreshed(listOf(Resource.Error("HTTP 502"), Resource.Success("成绩"), 6)))
    }
}
