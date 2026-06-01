package com.chen.memorizewords.navigation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.chen.memorizewords.core.navigation.AppRoute
import com.chen.memorizewords.core.navigation.AppLaunchEntry
import com.chen.memorizewords.core.navigation.AuthEntry
import com.chen.memorizewords.core.navigation.FeedbackEntry
import com.chen.memorizewords.core.navigation.HomeEntry
import com.chen.memorizewords.core.navigation.LearningEntry
import com.chen.memorizewords.core.navigation.LearningSessionRequest
import com.chen.memorizewords.core.navigation.OnboardingEntry
import com.chen.memorizewords.core.navigation.PracticeEntry
import com.chen.memorizewords.core.navigation.RouteNavigator
import com.chen.memorizewords.core.navigation.WordBookEntry
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppRouteNavigatorModule {
    @Binds
    @Singleton
    abstract fun bindRouteNavigator(impl: DefaultRouteNavigator): RouteNavigator
}

@Singleton
class DefaultRouteNavigator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLaunchEntry: AppLaunchEntry,
    private val homeEntry: HomeEntry,
    private val authEntry: AuthEntry,
    private val onboardingEntry: OnboardingEntry,
    private val wordBookEntry: WordBookEntry,
    private val feedbackEntry: FeedbackEntry,
    private val learningEntry: LearningEntry,
    private val practiceEntry: PracticeEntry
) : RouteNavigator {

    override fun navigate(route: AppRoute) {
        val intent = when (route) {
            AppRoute.Launch -> appLaunchEntry.createLaunchIntent(context)
            AppRoute.Home -> homeEntry.createHomeIntent(context)
            is AppRoute.Auth -> authEntry.createAuthIntent(context).apply {
                route.deepLink?.let { deepLink ->
                    action = Intent.ACTION_VIEW
                    data = deepLink.toUri()
                }
            }
            AppRoute.Onboarding -> onboardingEntry.createOnboardingIntent(context)
            is AppRoute.WordBook -> wordBookEntry.createWordBookIntent(context, route.deepLink)
            is AppRoute.Feedback -> feedbackEntry.createFeedbackIntent(context, route.deepLink)
            is AppRoute.Learning -> learningEntry.createLearningIntent(
                context,
                LearningSessionRequest(
                    wordIds = route.wordIds,
                    sessionType = route.sessionType,
                    sessionWordCount = route.sessionWordCount
                )
            )
            is AppRoute.OpenWord -> learningEntry.createOpenWordIntent(
                context,
                route.wordId,
                route.fromFloating
            )
            is AppRoute.Practice -> practiceEntry.createPracticeIntent(
                context = context,
                modeName = route.modeName,
                randomCount = route.randomCount,
                entryTypeName = route.entryTypeName,
                entryCount = route.entryCount,
                selectedIds = route.selectedIds
            )
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
