package com.gpetuhov.android.hive.utils.dagger.modules

import android.content.Context
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.auth.Auth
import com.gpetuhov.android.hive.domain.network.Network
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.domain.util.Messages
import com.gpetuhov.android.hive.managers.*
import com.gpetuhov.android.hive.repository.Repository
import com.gpetuhov.android.hive.util.MessagesProvider
import com.gpetuhov.android.hive.utils.TestNetworkManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class TestAppModule {

    @Provides
    @Singleton
    // TODO: mock this
    fun providesContext(): Context = HiveApp.application.applicationContext

    @Provides
    @Singleton
    // TODO: mock this
    fun providesLocationManager(context: Context) = LocationManager(context)

    @Provides
    @Singleton
    // TODO: mock this
    fun providesMapManager() = MapManager()

    @Provides
    @Singleton
    // TODO: mock this
    fun providesNotificationManager() = NotificationManager()

    @Provides
    @Singleton
    // TODO: mock this
    fun providesAuth(): Auth = AuthManager()

    @Provides
    @Singleton
    fun providesNetwork(): Network = TestNetworkManager()

    @Provides
    @Singleton
    // TODO: mock this
    fun providesMessages(): Messages = MessagesProvider()

    @Provides
    @Singleton
    // TODO: mock this
    fun providesRepo(): Repo = Repository()
}