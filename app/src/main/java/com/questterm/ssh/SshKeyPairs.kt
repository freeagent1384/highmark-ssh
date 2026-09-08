package com.questterm.ssh

import android.util.Base64
import com.trilead.ssh2.crypto.keys.Ed25519KeyPairGenerator
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.Ed25519Verify
import java.security.KeyPair
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Generates ed25519 SSH keypairs on-device for public-key authentication, using
 * sshlib's own Ed25519 key types directly (Android's JCA has no built-in Ed25519
 * support on all supported API levels, and these types expose plain constructors
 * so no Provider/KeyFactory registration is needed).
 */
object SshKeyPairs {

    fun generate(): KeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.initialize(256, SecureRandom())
        return generator.generateKeyPair()
    }

    /** Base64 of the PKCS8-encoded private key, for encryption at rest. */
    fun encodePrivateKey(keyPair: KeyPair): String =
        Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

    fun decodePrivateKey(base64: String): Ed25519PrivateKey =
        Ed25519PrivateKey(PKCS8EncodedKeySpec(Base64.decode(base64, Base64.NO_WRAP)))

    /** Base64 of the raw 32-byte ed25519 public point. Not sensitive; stored unencrypted. */
    fun encodePublicKeyRaw(publicKey: Ed25519PublicKey): String =
        Base64.encodeToString(publicKey.getAbyte(), Base64.NO_WRAP)

    fun decodePublicKeyRaw(base64: String): Ed25519PublicKey =
        Ed25519PublicKey(Base64.decode(base64, Base64.NO_WRAP))

    /** The "ssh-ed25519 AAAA... comment" line to add to the server's authorized_keys. */
    fun formatOpenSsh(publicKey: Ed25519PublicKey, comment: String): String? = try {
        val wireBytes = Ed25519Verify.get().encodePublicKey(publicKey)
        "ssh-ed25519 " + Base64.encodeToString(wireBytes, Base64.NO_WRAP) + " " + comment
    } catch (_: Exception) {
        null
    }
}
