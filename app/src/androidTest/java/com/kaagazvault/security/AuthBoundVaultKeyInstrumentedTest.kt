package com.kaagazvault.security

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AuthBoundVaultKeyInstrumentedTest {
    @Test
    fun generatedWrappingKeyRejectsCryptographicUseBeforeAuthentication() {
        val alias = AuthBoundVaultKey().newAliasForRecoveryOrSetup()
        val key = AuthBoundVaultKey(alias = alias)
        val plaintext = ByteArray(32) { (it + 1).toByte() }

        try {
            val cipher = key.prepareSetupCipher()
            assertTrue("setup must create its unique test alias", key.hasWrappingKeyAlias())
            val unauthenticatedFailure = try {
                cipher.doFinal(plaintext)
                null
            } catch (failure: GeneralSecurityException) {
                failure
            }
            assertTrue(
                "Auth-per-use Keystore must reject use until authentication; got no denial",
                unauthenticatedFailure != null
            )
            val causeChain = generateSequence(unauthenticatedFailure) { it.cause }
                .joinToString(" | ") { "${it.javaClass.name}: ${it.message.orEmpty()}" }
            assertTrue(
                "Expected a Keystore user-authentication denial, got: $causeChain",
                causeChain.contains("Key user not authenticated", ignoreCase = true) ||
                    causeChain.contains("KEY_USER_NOT_AUTHENTICATED", ignoreCase = true) ||
                    causeChain.contains("UserNotAuthenticatedException", ignoreCase = true)
            )
        } finally {
            plaintext.fill(0)
            key.discardAbandonedSetup()
        }

        assertFalse("test cleanup must remove only its unique alias", key.hasWrappingKeyAlias())
    }

    @Test
    fun missingWrappingAliasFailsClosedWithoutChangingSyntheticEnvelope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = AuthBoundVaultKey().newAliasForRecoveryOrSetup()
        val key = AuthBoundVaultKey(alias = alias)
        val syntheticEnvelope = ByteArray(28) { (it * 3 + 7).toByte() }
        val envelopeFile = File.createTempFile(
            "vault-missing-key-${UUID.randomUUID()}-",
            ".bin",
            context.cacheDir
        )

        try {
            envelopeFile.writeBytes(syntheticEnvelope)
            val before = envelopeFile.readBytes()

            assertThrows(MissingEncryptionKeyException::class.java) {
                key.prepareUnlockCipher(syntheticEnvelope)
            }
            assertFalse("missing alias must not be silently regenerated", key.hasWrappingKeyAlias())
            assertArrayEquals("synthetic protected bytes must remain unchanged", before, envelopeFile.readBytes())
            before.fill(0)
        } finally {
            syntheticEnvelope.fill(0)
            // This is a uniquely created cache fixture, not a production vault data path.
            envelopeFile.delete()
        }
    }
}
