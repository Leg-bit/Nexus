package com.nexus.media.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nexus.media.MainActivity
import com.nexus.media.di.AppContainer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var container: AppContainer
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val base = container.player.player

        // The notification/lock-screen buttons talk to this wrapper: previous/next are always available
        // (next wraps to the first song at the end of the queue).
        val wrapper = object : ForwardingPlayer(base) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    ).build()

            override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

            private fun goNext() {
                if (base.hasNextMediaItem()) base.seekToNextMediaItem()
                else if (base.mediaItemCount > 0) base.seekTo(0, 0L)
                base.play()
            }

            private fun goPrevious() {
                if (base.currentPosition > 3000 || !base.hasPreviousMediaItem()) base.seekTo(0)
                else base.seekToPreviousMediaItem()
            }

            override fun seekToNext() = goNext()
            override fun seekToNextMediaItem() = goNext()
            override fun seekToPrevious() = goPrevious()
            override fun seekToPreviousMediaItem() = goPrevious()
        }

        // tapping the notification opens the app
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, wrapper).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
