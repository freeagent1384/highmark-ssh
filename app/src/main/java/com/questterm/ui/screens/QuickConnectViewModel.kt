package com.questterm.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questterm.data.AuthMethod
import com.questterm.data.ConnectionHistoryStore
import com.questterm.data.ConnectionProfile
import com.questterm.session.SessionManager
import com.questterm.ssh.ConnectionCache
import com.questterm.ssh.KnownHostsStore
import com.questterm.ssh.SshAuth
import com.questterm.ssh.SshConnectionManager
import com.questterm.ssh.SshKeyPairs
import com.questterm.terminal.termux.QuestTermViewClient
import com.questterm.terminal.termux.SshTerminalSession
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.KeyPair
import javax.inject.Inject

data class QuickConnectUiState(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val isConnecting: Boolean = false,
    val error: String? = null,
    val hostKeyPrompt: HostKeyPrompt? = null,
    val savedProfiles: List<ConnectionProfile> = emptyList(),
    val rememberPassword: Boolean = false,
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    /** The "ssh-ed25519 AAAA... comment" line for the currently generated key, if any. */
    val generatedPublicKey: String? = null,
)

/** Shown when the user needs to accept or reject a host key. */
data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    /** True if the key changed from a previously accepted one (possible MITM). */
    val keyChanged: Boolean,
)

@HiltViewModel
class QuickConnectViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val sshConnectionManager: SshConnectionManager,
    private val connectionCache: ConnectionCache,
    private val connectionHistory: ConnectionHistoryStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickConnectUiState())
    val uiState: StateFlow<QuickConnectUiState> = _uiState

    /** Set by the verifier on the IO thread, completed by the UI when user responds. */
    private var hostKeyResponse: CompletableDeferred<Boolean>? = null

    /** The keypair backing generatedPublicKey/authMethod == KEY. Not exposed via state
     *  since KeyPair isn't a plain data value; kept alongside it here instead. */
    private var pendingKeyPair: KeyPair? = null

    private val knownHosts: KnownHostsStore
        get() = sshConnectionManager.knownHostsStore

    init {
        loadSavedProfiles()
        val cached = connectionCache.load()
        if (cached != null) {
            // Check if a saved profile exists with a stored password
            val matchingProfile = _uiState.value.savedProfiles.find {
                it.host == cached.host &&
                    it.port.toString() == cached.port &&
                    it.username == cached.username
            }
            _uiState.value = _uiState.value.copy(
                host = cached.host,
                port = cached.port,
                username = cached.username,
                password = cached.password,
                rememberPassword = matchingProfile?.encryptedPassword != null,
            )
        } else {
            // Pre-fill with default demo connection if no cache exists
            _uiState.value.savedProfiles.firstOrNull()?.let { default ->
                _uiState.value = _uiState.value.copy(
                    host = default.host,
                    port = default.port.toString(),
                    username = default.username,
                    password = default.getDecryptedPassword() ?: "bandit0",
                )
            }
        }
    }

    private fun loadSavedProfiles() {
        _uiState.update {
            it.copy(savedProfiles = connectionHistory.getAllProfiles())
        }
    }

    fun updateHost(value: String) {
        var host = value
        var username = _uiState.value.username
        var port = _uiState.value.port

        if ("@" in value) {
            val parts = value.split("@", limit = 2)
            username = parts[0]
            host = parts[1]
        }
        if (":" in host) {
            val parts = host.split(":", limit = 2)
            host = parts[0]
            val portStr = parts[1]
            if (portStr.all { it.isDigit() } && portStr.isNotEmpty()) {
                port = portStr
            }
        }

        _uiState.value = _uiState.value.copy(host = host, username = username, port = port)
    }

    fun updatePort(value: String) {
        _uiState.value = _uiState.value.copy(port = value)
    }

    fun updateUsername(value: String) {
        _uiState.value = _uiState.value.copy(username = value)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun toggleRememberPassword() {
        _uiState.update { it.copy(rememberPassword = !it.rememberPassword) }
    }

    fun selectAuthMethod(method: AuthMethod) {
        _uiState.update { it.copy(authMethod = method) }
        if (method == AuthMethod.KEY && pendingKeyPair == null) {
            regenerateKey()
        }
    }

    /** Generates a fresh on-device ed25519 keypair and shows its public half for export. */
    fun regenerateKey() {
        val keyPair = SshKeyPairs.generate()
        pendingKeyPair = keyPair
        val state = _uiState.value
        val comment = state.username.ifBlank { "quest" } + "@" + state.host.ifBlank { "highmark-ssh" }
        val openSsh = SshKeyPairs.formatOpenSsh(keyPair.public as Ed25519PublicKey, comment)
        _uiState.update { it.copy(authMethod = AuthMethod.KEY, generatedPublicKey = openSsh) }
    }

    fun selectProfile(profile: ConnectionProfile) {
        pendingKeyPair = if (profile.authMethod == AuthMethod.KEY) profile.getKeyPair() else null
        _uiState.update {
            it.copy(
                host = profile.host,
                port = profile.port.toString(),
                username = profile.username,
                password = if (profile.authMethod == AuthMethod.PASSWORD) profile.getDecryptedPassword() ?: "" else "",
                rememberPassword = profile.encryptedPassword != null,
                authMethod = profile.authMethod,
                generatedPublicKey = if (profile.authMethod == AuthMethod.KEY) profile.getOpenSshPublicKey() else null,
            )
        }
    }

    fun toggleFavorite(profileId: String) {
        connectionHistory.toggleFavorite(profileId)
        loadSavedProfiles()
    }

    fun deleteProfile(profileId: String) {
        connectionHistory.deleteProfile(profileId)
        loadSavedProfiles()
    }

    fun acceptHostKey() {
        hostKeyResponse?.complete(true)
        _uiState.value = _uiState.value.copy(hostKeyPrompt = null)
    }

    fun rejectHostKey() {
        hostKeyResponse?.complete(false)
        _uiState.value = _uiState.value.copy(hostKeyPrompt = null)
    }

    fun connect(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.host.isBlank() || state.username.isBlank()) {
            _uiState.value = state.copy(error = "Host and username are required")
            return
        }
        val keyPair = pendingKeyPair
        if (state.authMethod == AuthMethod.KEY && keyPair == null) {
            _uiState.value = state.copy(error = "Generate a key first")
            return
        }

        val port = state.port.toIntOrNull() ?: 22

        _uiState.value = state.copy(isConnecting = true, error = null)

        val auth = when (state.authMethod) {
            AuthMethod.PASSWORD -> SshAuth.Password(state.password)
            AuthMethod.KEY -> SshAuth.PrivateKey(keyPair!!)
        }

        val sshSession = sshConnectionManager.createSession(
            host = state.host,
            port = port,
            username = state.username,
            auth = auth,
        )

        // Host key verifier: TOFU with user confirmation
        val verifier = ServerHostKeyVerifier { hostname, sshPort, keyAlgorithm, hostKey ->
            val result = knownHosts.verify(hostname, sshPort, keyAlgorithm, hostKey)
            when (result) {
                KnownHostsStore.VerifyResult.MATCH -> true
                KnownHostsStore.VerifyResult.UNKNOWN,
                KnownHostsStore.VerifyResult.CHANGED -> {
                    val deferred = CompletableDeferred<Boolean>()
                    hostKeyResponse = deferred
                    _uiState.value = _uiState.value.copy(
                        hostKeyPrompt = HostKeyPrompt(
                            host = hostname,
                            port = sshPort,
                            algorithm = keyAlgorithm,
                            fingerprint = KnownHostsStore.fingerprint(hostKey),
                            keyChanged = result == KnownHostsStore.VerifyResult.CHANGED,
                        ),
                    )
                    // Block the IO thread until user responds — this is intentional,
                    // the SSH handshake can't continue until the key is verified
                    val accepted = runBlocking { deferred.await() }
                    if (accepted) {
                        knownHosts.accept(hostname, sshPort, keyAlgorithm, hostKey)
                    }
                    accepted
                }
            }
        }

        val viewClient = QuestTermViewClient(appContext)
        val terminalSession = SshTerminalSession(sshSession, viewClient)

        viewModelScope.launch {
            try {
                terminalSession.connect(hostKeyVerifier = verifier)
                if (state.authMethod == AuthMethod.PASSWORD) {
                    connectionCache.save(state.host, state.port.ifBlank { "22" }, state.username, state.password)
                }
                val label = "${state.username}@${state.host}"
                sessionManager.addTab(label, sshSession, terminalSession, viewClient)

                // Save to connection history
                val profile = ConnectionProfile(
                    host = state.host,
                    port = port,
                    username = state.username,
                ).let {
                    when (state.authMethod) {
                        AuthMethod.PASSWORD -> if (state.rememberPassword) it.withPassword(state.password) else it
                        AuthMethod.KEY -> it.withGeneratedKey(keyPair!!)
                    }
                }
                connectionHistory.saveProfile(profile, rememberPassword = state.rememberPassword)
                loadSavedProfiles()

                _uiState.value = _uiState.value.copy(isConnecting = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    error = e.message ?: "Connection failed",
                )
            }
        }
    }
}
