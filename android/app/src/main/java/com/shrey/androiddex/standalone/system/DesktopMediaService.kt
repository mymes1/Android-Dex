package com.shrey.androiddex.standalone.system

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.asImageBitmap
import com.shrey.androiddex.standalone.data.MediaSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional, user-granted notification access used only for the original-style media widget and
 * notification count. Notification text, titles, bodies and input history are never read or stored.
 */
class DesktopMediaService : NotificationListenerService() {
    companion object {
        private val _state = MutableStateFlow(MediaSnapshot())
        val state = _state.asStateFlow()
        @Volatile private var instance: DesktopMediaService? = null

        fun isAccessGranted(context: Context): Boolean = runCatching {
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(context, DesktopMediaService::class.java))
        }.getOrDefault(false)

        fun refreshAccess(context: Context) {
            val granted = isAccessGranted(context)
            _state.value = _state.value.copy(accessGranted = granted,
                hasSession = granted && _state.value.hasSession,
                notificationCount = if (granted) _state.value.notificationCount else 0)
        }

        fun playPause() { instance?.togglePlayback() }
        fun next() { instance?.controller?.transportControls?.skipToNext() }
        fun previous() { instance?.controller?.transportControls?.skipToPrevious() }
    }

    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var controller: MediaController? = null
    private var controllers: List<MediaController> = emptyList()
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { publishMedia() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { publishMedia() }
        override fun onSessionDestroyed() { selectController() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        controllers = list ?: emptyList()
        selectController()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        component = ComponentName(this, DesktopMediaService::class.java)
        manager = getSystemService(MediaSessionManager::class.java)
        runCatching { manager?.addOnActiveSessionsChangedListener(sessionsListener, component!!) }
        runCatching { controllers = manager?.getActiveSessions(component) ?: emptyList() }
        selectController()
        updateCount()
        _state.value = _state.value.copy(accessGranted = true)
    }

    override fun onListenerDisconnected() {
        cleanup()
        _state.value = MediaSnapshot(accessGranted = isAccessGranted(this))
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        if (instance === this) { instance = null; _state.value = MediaSnapshot() }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { updateCount() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { updateCount() }

    private fun cleanup() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        controllers = emptyList()
        manager = null
        if (instance === this) instance = null
    }

    private fun updateCount() {
        val count = runCatching {
            activeNotifications?.count { it.packageName != packageName && !it.isOngoing && it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0 } ?: 0
        }.getOrDefault(0)
        _state.value = _state.value.copy(accessGranted = true, notificationCount = count)
    }

    private fun selectController() {
        controller?.unregisterCallback(callback)
        controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
        controller?.registerCallback(callback)
        publishMedia()
    }

    private fun togglePlayback() {
        val current = controller ?: return
        if (current.playbackState?.state == PlaybackState.STATE_PLAYING) current.transportControls.pause()
        else current.transportControls.play()
    }

    private fun publishMedia() {
        val current = controller
        if (current == null) {
            _state.value = _state.value.copy(hasSession = false, title = "", artist = "", playing = false, packageName = "", artwork = null)
            return
        }
        val metadata = current.metadata
        val bitmap = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        _state.value = _state.value.copy(
            accessGranted = true, hasSession = true,
            title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)?.take(120) ?: current.packageName,
            artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)?.take(120) ?: "",
            playing = current.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = current.packageName,
            artwork = runCatching { bitmap?.asImageBitmap() }.getOrNull()
        )
    }
}
