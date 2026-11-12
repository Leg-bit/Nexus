package com.nexus.media.player.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nexus.media.MainActivity
import com.nexus.media.R
import com.nexus.media.di.AppContainer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var container: AppContainer
    private var session: MediaSession? = null
    private lateinit var base: Player

    private val cmdShuffle = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
    private val cmdRepeat = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)

    // keep the extra notification buttons (shuffle / repeat) in sync with the player
    private val stateListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { session?.setCustomLayout(layout()) }
        override fun onRepeatModeChanged(repeatMode: Int) { session?.setCustomLayout(layout()) }
    }

    private fun layout(): ImmutableList<CommandButton> {
        val shuffleOn = base.shuffleModeEnabled
        val rep = base.repeatMode
        val shuffle = CommandButton.Builder()
            .setDisplayName(if (shuffleOn) "Acak: aktif" else "Acak")
            .setIconResId(if (shuffleOn) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
            .setSessionCommand(cmdShuffle)
            .build()
        val repeat = CommandButton.Builder()
            .setDisplayName(
                when (rep) {
                    Player.REPEAT_MODE_ONE -> "Ulangi satu lagu"
                    Player.REPEAT_MODE_ALL -> "Ulangi semua"
                    else -> "Ulangi"
                }
            )
            .setIconResId(
                when (rep) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                    else -> R.drawable.ic_repeat
                }
            )
            .setSessionCommand(cmdRepeat)
            .build()
        return ImmutableList.of(shuffle, repeat)
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(cmdShuffle).add(cmdRepeat).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(cmds).build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> container.player.setShuffle(!base.shuffleModeEnabled)
                ACTION_REPEAT -> container.player.cycleRepeat()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = container.player
        base = pm.player

        // The notification/lock-screen buttons talk to this wrapper: previous/next are always available and
        // behave exactly like the in-app buttons; play also restarts a finished queue.
        val wrapper = object : ForwardingPlayer(base) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    ).build()

            override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

            override fun play() = pm.resume()
            override fun seekToNext() = pm.next()
            override fun seekToNextMediaItem() = pm.next()
            override fun seekToPrevious() = pm.previous()
            override fun seekToPreviousMediaItem() = pm.previous()
        }

        // tapping the notification opens the app
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, wrapper)
            .setSessionActivity(open)
            .setCallback(Callback())
            .setCustomLayout(layout())
            .build()
        base.addListener(stateListener)
        // when the cover colour is known, refresh the notification so it picks up the new tint
        pm.onAccentChanged = { session?.setCustomLayout(layout()) }
        setMediaNotificationProvider(NexusNotificationProvider(this) { pm.notifColor })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        if (::base.isInitialized) base.removeListener(stateListener)
        container.player.onAccentChanged = null
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_SHUFFLE = "com.nexus.media.SHUFFLE"
        const val ACTION_REPEAT = "com.nexus.media.REPEAT"
    }
}
