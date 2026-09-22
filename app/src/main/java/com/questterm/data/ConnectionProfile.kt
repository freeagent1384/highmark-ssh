package com.questterm.data

import com.questterm.ssh.CredentialEncryption
import com.questterm.ssh.SshKeyPairs
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import java.security.KeyPair
import java.util.UUID

enum class AuthMethod { PASSWORD, KEY }

data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val port: Int,
    val username: String,
    val encryptedPassword: String? = null,  // null if not remembered
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val encryptedPrivateKey: String? = null,  // PKCS8, encrypted; set when authMethod == KEY
    val publicKeyRaw: String? = null,  // raw 32-byte ed25519 point, base64 (not sensitive)
    val isFavorite: Boolean = false,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
) {
    val displayLabel: String
        get() = "$username@$host:$port"

    fun withPassword(password: String): ConnectionProfile =
        copy(
            authMethod = AuthMethod.PASSWORD,
            encryptedPassword = CredentialEncryption.encrypt(password),
        )

    fun getDecryptedPassword(): String? =
        encryptedPassword?.let { CredentialEncryption.decrypt(it) }

    /** Generates a new on-device ed25519 keypair and attaches it to this profile. */
    fun withGeneratedKey(keyPair: KeyPair): ConnectionProfile =
        copy(
            authMethod = AuthMethod.KEY,
            encryptedPrivateKey = CredentialEncryption.encrypt(SshKeyPairs.encodePrivateKey(keyPair)),
            publicKeyRaw = SshKeyPairs.encodePublicKeyRaw(keyPair.public as Ed25519PublicKey),
        )

    fun getKeyPair(): KeyPair? {
        val encryptedPrivate = encryptedPrivateKey?.takeIf { it.isNotEmpty() } ?: return null
        val rawPublic = publicKeyRaw?.takeIf { it.isNotEmpty() } ?: return null
        val decryptedPrivate = CredentialEncryption.decrypt(encryptedPrivate).takeIf { it.isNotEmpty() } ?: return null
        return try {
            KeyPair(SshKeyPairs.decodePublicKeyRaw(rawPublic), SshKeyPairs.decodePrivateKey(decryptedPrivate))
        } catch (_: Exception) {
            null
        }
    }

    /** The public key line to add to the server's ~/.ssh/authorized_keys, if this profile uses key auth. */
    fun getOpenSshPublicKey(): String? =
        publicKeyRaw?.let { SshKeyPairs.formatOpenSsh(SshKeyPairs.decodePublicKeyRaw(it), displayLabel) }

    fun updateLastUsed(): ConnectionProfile =
        copy(lastUsedTimestamp = System.currentTimeMillis())
}
