package com.kaagazvault.security

import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthBoundVaultKeyCleanupTest {
    @Test
    fun discardAbandonedSetupRemovesOnlyTheUncommittedAlias() {
        val keyStore = KeyStore.getInstance("JCEKS").apply { load(null) }
        val abandonedAlias = "kaagaz_vault_auth_bound_vek_wrap_v1_${"a".repeat(32)}"
        val siblingAlias = "kaagaz_vault_auth_bound_vek_wrap_v1_${"b".repeat(32)}"
        val protection = KeyStore.PasswordProtection("test-password".toCharArray())
        val secret = SecretKeySpec(ByteArray(16) { 7 }, "AES")
        keyStore.setEntry(abandonedAlias, KeyStore.SecretKeyEntry(secret), protection)
        keyStore.setEntry(siblingAlias, KeyStore.SecretKeyEntry(secret), protection)
        assertTrue(keyStore.containsAlias(abandonedAlias))
        assertTrue(keyStore.containsAlias(siblingAlias))

        AuthBoundVaultKey(abandonedAlias, keyStoreProvider = { keyStore }).discardAbandonedSetup()

        assertFalse(keyStore.containsAlias(abandonedAlias))
        assertTrue("cleanup must not remove unrelated aliases", keyStore.containsAlias(siblingAlias))
    }

    @Test
    fun discardAbandonedSetupIsIdempotentWhenAliasIsAlreadyAbsent() {
        val keyStore = KeyStore.getInstance("JCEKS").apply { load(null) }
        val alias = "kaagaz_vault_auth_bound_vek_wrap_v1_${"c".repeat(32)}"

        AuthBoundVaultKey(alias, keyStoreProvider = { keyStore }).discardAbandonedSetup()

        assertFalse(keyStore.containsAlias(alias))
    }
}
