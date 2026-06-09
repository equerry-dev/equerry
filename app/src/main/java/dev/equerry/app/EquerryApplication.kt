package dev.equerry.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. [@HiltAndroidApp] generates the Hilt dependency-injection
 * container that the rest of the app (drivers, slot routing, view models) is wired through.
 */
@HiltAndroidApp
class EquerryApplication : Application()
