package com.wayars.app

import android.content.Context
import com.wayars.app.data.local.AppDatabase
import com.wayars.app.data.prefs.SettingsDataStore
import com.wayars.app.data.repository.OrderRepositoryImpl
import com.wayars.app.data.repository.SettingsRepositoryImpl
import com.wayars.app.domain.repository.OrderRepository
import com.wayars.app.domain.repository.SettingsRepository
import com.wayars.app.domain.usecase.EvaluateOrderUseCase

/**
 * Minimal hand-rolled DI container (no Hilt/Koin dependency needed for this
 * app's size). Access via [WayArsApplication.container] or [appContainer].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val settingsDataStore by lazy { SettingsDataStore(appContext) }
    private val database by lazy { AppDatabase.getInstance(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(settingsDataStore) }
    val orderRepository: OrderRepository by lazy { OrderRepositoryImpl(database.orderDao()) }
    val evaluateOrderUseCase by lazy { EvaluateOrderUseCase() }
}

/** Convenience accessor from any [Context]. */
fun Context.appContainer(): AppContainer = (applicationContext as WayArsApplication).container
