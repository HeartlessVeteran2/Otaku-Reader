package app.otakureader.data.di

import app.otakureader.data.tracker.BaseTracker
import app.otakureader.data.tracker.anilist.AniListTracker
import app.otakureader.data.tracker.kitsu.KitsuTracker
import app.otakureader.data.tracker.mal.MyAnimeListTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Provides [BaseTracker] implementations as a Hilt multibinding set so that
 * [app.otakureader.data.tracker.TrackManager] can be injected with all registered trackers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    @IntoSet
    abstract fun bindMyAnimeListTracker(tracker: MyAnimeListTracker): BaseTracker

    @Binds
    @IntoSet
    abstract fun bindAniListTracker(tracker: AniListTracker): BaseTracker

    @Binds
    @IntoSet
    abstract fun bindKitsuTracker(tracker: KitsuTracker): BaseTracker
}
