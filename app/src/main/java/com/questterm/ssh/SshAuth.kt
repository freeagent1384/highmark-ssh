package com.questterm.ssh

import java.security.KeyPair

/** How to authenticate an [SshSession] to the remote server. */
sealed class SshAuth {
    data class Password(val password: String) : SshAuth()
    data class PrivateKey(val keyPair: KeyPair) : SshAuth()
}
