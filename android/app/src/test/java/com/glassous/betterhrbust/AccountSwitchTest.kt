package com.glassous.betterhrbust

import com.glassous.betterhrbust.data.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Test

class AccountSwitchTest {
    @Test fun refreshedCaptchaSessionDoesNotCauseAnEndlessAccountSwitchLoop() {
        assertTrue(AuthRepository.needsAccountPreparation("A", null, "B", true))
        assertFalse(AuthRepository.needsAccountPreparation("A", "B", "B", true))
        assertTrue(AuthRepository.needsAccountPreparation("A", "B", "C", true))
        assertFalse(AuthRepository.needsAccountPreparation("B", null, "B", true))
        assertFalse(AuthRepository.needsAccountPreparation(null, null, "B", true))
        assertFalse(AuthRepository.needsAccountPreparation("A", null, "B", false))
    }
}
