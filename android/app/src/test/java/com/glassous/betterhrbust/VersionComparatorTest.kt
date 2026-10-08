package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.util.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本更新检测的版本比较单元测试
 *
 * 口径与桌面端 web/src/services/update.js 一致：
 * 剥离 v / desktop-v / android-v 等前缀后按 `.` 分段数值比较，段数不同按缺位补 0。
 */
class VersionComparatorTest {

    @Test
    fun testNormalize_StripsTagPrefixes() {
        assertEquals("1.0.0", VersionComparator.normalize("v1.0.0"))
        assertEquals("1.0.0", VersionComparator.normalize("desktop-v1.0.0"))
        assertEquals("1.2.3", VersionComparator.normalize("android-v1.2.3"))
        assertEquals("1.0", VersionComparator.normalize("1.0"))
        assertEquals("2.0.0", VersionComparator.normalize("  v2.0.0  "))
    }

    @Test
    fun testNormalize_NoVersionInfoReturnsEmpty() {
        assertEquals("", VersionComparator.normalize(null))
        assertEquals("", VersionComparator.normalize(""))
        assertEquals("", VersionComparator.normalize("   "))
        assertEquals("", VersionComparator.normalize("latest"))
    }

    @Test
    fun testNormalize_IgnoresPrereleaseSuffix() {
        assertEquals("1.0.0", VersionComparator.normalize("v1.0.0-beta.1"))
        assertEquals("1.0.0", VersionComparator.normalize("1.0.0-rc1"))
    }

    @Test
    fun testCompare_NumericPerSegmentNotLexicographic() {
        assertEquals(1, VersionComparator.compare("1.10.0", "1.9.0"))
        assertEquals(-1, VersionComparator.compare("1.9.0", "1.10.0"))
        assertEquals(0, VersionComparator.compare("1.2", "1.2"))
    }

    @Test
    fun testCompare_DifferentSegmentCounts() {
        assertEquals(0, VersionComparator.compare("1.0", "1.0.0"))
        assertEquals(1, VersionComparator.compare("1.0.1", "1.0"))
        assertEquals(-1, VersionComparator.compare("1.0", "1.0.1"))
    }

    @Test
    fun testCompare_TagPrefixAndMajorVersion() {
        assertEquals(1, VersionComparator.compare("v2.0.0", "1.9.9"))
        assertEquals(1, VersionComparator.compare("android-v1.0.1", "1.0.0"))
        assertEquals(-1, VersionComparator.compare("0.9", "1.0.0"))
    }

    @Test
    fun testIsNewer_SameVersionIsNotNewer() {
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun testIsNewer_DetectsHigherVersion() {
        assertTrue(VersionComparator.isNewer("v1.0.1", "1.0.0"))
        assertTrue(VersionComparator.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun testIsNewer_NoVersionInfoStaysConservative() {
        assertFalse(VersionComparator.isNewer(null, "1.0.0"))
        assertFalse(VersionComparator.isNewer("", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0.0", null))
    }
}
