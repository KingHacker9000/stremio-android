package com.stremio.mobile.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Safe fallback used when the native stream-server JNI library cannot be loaded.
 *
 * The streaming server is an optional capability for browsing/authentication and
 * must never make the entire application unusable. Keep the controller stopped
 * so server-dependent playback/download paths can handle the missing capability
 * at the point of use without triggering the app-wide fatal server dialog.
 */
class StubStreamingServerController : StreamingServerController {
    private val mutableState = MutableStateFlow<StreamingServerState>(StreamingServerState.Stopped)

    override val state: StateFlow<StreamingServerState> = mutableState

    override suspend fun start() {
        // Deliberately remain stopped. A missing optional JNI library should not
        // transition global application state to Failed and block login/browsing.
        mutableState.value = StreamingServerState.Stopped
    }

    override suspend fun stop() {
        mutableState.value = StreamingServerState.Stopped
    }
}
