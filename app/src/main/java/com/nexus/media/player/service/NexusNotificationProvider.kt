package com.nexus.media.player.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.nexus.media.R

/** Default Media3 notification, tinted with a colour taken from the current cover (colorized) and our small icon. */
@UnstableApi
class NexusNotificationProvider(ctx: Context, private val color: () -> Int) : MediaNotification.Provider {
    private val base = DefaultMediaNotificationProvider(ctx).also { it.setSmallIcon(R.drawable.ic_stat_nexus) }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val mn = base.createNotification(mediaSession, customLayout, actionFactory, onNotificationChangedCallback)
        mn.notification.color = color()
        mn.notification.extras.putBoolean("android.colorized", true)
        return mn
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        base.handleCustomCommand(session, action, extras)
}
