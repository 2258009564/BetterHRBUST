package com.glassous.betterhrbust

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glassous.betterhrbust.core.database.AppDatabase
import com.glassous.betterhrbust.core.database.ProfileEntity
import com.glassous.betterhrbust.core.datastore.UserPreferencesManager
import com.glassous.betterhrbust.core.model.PersonalInfo
import com.glassous.betterhrbust.core.network.AcademicHttpClient
import com.glassous.betterhrbust.data.repository.AcademicRepository
import com.glassous.betterhrbust.data.repository.Resource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AccountIsolationTest {
    @Test fun actualRoomAndDataStoreRejectOtherProfilesAndDelayedAccountResults() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"isolation-${System.nanoTime()}.preferences_pb")
        val storeScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val store=PreferenceDataStoreFactory.create(scope=storeScope, produceFile={file})
        val prefs=UserPreferencesManager(context,store)
        val database=Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val a="2401234567"; val b="2407654321"
        try {
            prefs.saveAuth(a,"101","2026","1")
            val repo=AcademicRepository(AcademicHttpClient(),database,prefs)
            val profileB=PersonalInfo(studentNumber=b,realName="Student B")
            database.profileDao().insert(ProfileEntity(b,Json.encodeToString(profileB)))
            assertTrue(repo.getPersonalInfo(a,cacheOnly=true).last() is Resource.Error)
            database.profileDao().insert(ProfileEntity(a,Json.encodeToString(profileB)))
            assertTrue("错误缓存键不能让B的档案显示给A",repo.getPersonalInfo(a,cacheOnly=true).last() is Resource.Error)
            val profileA=PersonalInfo(studentNumber=a,realName="Student A")
            database.profileDao().insert(ProfileEntity(a,Json.encodeToString(profileA)))
            val valid=repo.getPersonalInfo(a,cacheOnly=true).last() as Resource.Success
            assertEquals(a,(valid.data as PersonalInfo).studentNumber)
            val old=prefs.preferencesFlow.first()
            prefs.saveAuth(b,"202","2026","1")
            prefs.setSessionState(true,expected=old)
            prefs.setLastPromptAt(123456,expected=old)
            prefs.setRealName("Student A stale",expected=old)
            prefs.setCurrentWeek(20,expected=old)
            prefs.setLastFullSyncDate("2099-01-01",expected=old)
            assertFalse(prefs.preferencesFlow.first().sessionExpired)
            assertEquals(0L,prefs.preferencesFlow.first().lastPromptAt)
            assertEquals("",prefs.preferencesFlow.first().realName)
            assertEquals(1,prefs.preferencesFlow.first().currentWeek)
            assertEquals("",prefs.preferencesFlow.first().lastFullSyncDate)

            prefs.saveAuth(a,"101","2026","1")
            val accepted=CountDownLatch(1); val release=CountDownLatch(1)
            ServerSocket(0).use { server ->
                val responder=launch(Dispatchers.IO) {
                    server.accept().use { socket ->
                        val reader=socket.getInputStream().bufferedReader()
                        while (reader.readLine()?.isNotEmpty()==true) { }
                        accepted.countDown()
                        check(release.await(10,TimeUnit.SECONDS))
                        val body="<table class='form'><tr><th>用户名</th><td>$a</td></tr><tr><th>真实姓名</th><td>Student A delayed</td></tr></table>".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray());write(body);flush()
                        }
                    }
                }
                val delayedRepo=AcademicRepository(AcademicHttpClient(baseUrl="http://127.0.0.1:${server.localPort}/academic/"),database,prefs)
                val result=async(Dispatchers.IO) {
                    runCatching { delayedRepo.getPersonalInfo(a,forceRefresh=true).last() }.exceptionOrNull()
                }
                check(withContext(Dispatchers.IO) { accepted.await(10,TimeUnit.SECONDS) })
                prefs.saveAuth(b,"202","2026","1")
                release.countDown()
                assertTrue("迟到A请求必须取消，不得回退显示A缓存",result.await() is CancellationException)
                responder.join()
                assertEquals("Student A",Json.decodeFromString<PersonalInfo>(database.profileDao().getProfile(a).first()!!.json).realName)
            }
        } finally {
            database.close(); storeScope.cancel(); storeScope.coroutineContext[Job]?.join();file.delete()
        }
    }
}
