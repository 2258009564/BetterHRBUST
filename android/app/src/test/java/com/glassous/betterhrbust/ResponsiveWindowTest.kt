package com.glassous.betterhrbust

import com.glassous.betterhrbust.core.ui.usesNavigationRail
import org.junit.Assert.*
import org.junit.Test

class ResponsiveWindowTest {
    @Test fun navigationFollowsUsableWindow() {
        assertTrue(usesNavigationRail(1280, 800))
        assertTrue(usesNavigationRail(800, 1280))
        assertFalse(usesNavigationRail(480, 800))
        assertFalse(usesNavigationRail(900, 400))
        assertTrue(usesNavigationRail(600, 480))
        assertFalse(usesNavigationRail(599, 800))
    }
}
