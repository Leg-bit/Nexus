package com.nexus.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nexus.media.core.theme.NexusTheme
import com.nexus.media.di.AppContainer
import com.nexus.media.ui.NexusApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexusTheme { NexusApp(container) } }
    }
}
