package app.otakureader.core.discord.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for Discord RPC dependencies.
 * DiscordRpcService uses @Inject constructor injection; no explicit @Provides needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object DiscordModule
