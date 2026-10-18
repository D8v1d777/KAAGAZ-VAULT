package com.kaagazvault.security

import org.junit.Assert.assertThrows
import org.junit.Test

class MissingEncryptionKeyPolicyTest {
    @Test
    fun permitsFirstKeyCreationWhenNoProtectedDataExists() {
        ensureNewKeyAllowed(hasExistingProtectedData = false)
    }

    @Test
    fun refusesReplacementWhenProtectedDataExists() {
        assertThrows(MissingEncryptionKeyException::class.java) {
            ensureNewKeyAllowed(hasExistingProtectedData = true)
        }
    }
}
