package com.example

import android.app.Application
import com.example.data.AytosRepository
import com.example.service.AytosOverlayManager
import com.example.ui.theme.ThemeState

class AytosApplication : Application() {

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeState.updateSystemDark(this)
    }

    lateinit var overlayManager: AytosOverlayManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Repository + theme (day/night) before any screen is drawn
        val repository = AytosRepository.getInstance(this)
        ThemeState.mode = repository.preferences.themeModeFlow.value
        ThemeState.updateSystemDark(this)

        // Initialize Overlay Manager
        overlayManager = AytosOverlayManager(this)
        overlayManager.startListening()

        // NOTE: the foreground service is started from MainActivity / the accessibility service
        // (inside try/catch). Starting it here crashes on Android 12+ whenever the system starts
        // the process in the background (e.g. to bind the accessibility service).
    }
}
