package io.github.jiangyuyi.lightnovel.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private const val FADE_DURATION = 180
private const val SLIDE_DURATION = 240

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnterTransition(
    isPop: Boolean,
): EnterTransition = when (pageMotion(initialState.destination.route, targetState.destination.route, isPop)) {
    PageMotion.NONE -> EnterTransition.None
    PageMotion.FADE -> fadeIn(tween(FADE_DURATION))
    PageMotion.FORWARD -> fadeIn(tween(SLIDE_DURATION)) + slideInHorizontally(
        tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
    ) { width -> width / 10 }
    PageMotion.BACKWARD -> fadeIn(tween(SLIDE_DURATION)) + slideInHorizontally(
        tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
    ) { width -> -width / 10 }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExitTransition(
    isPop: Boolean,
): ExitTransition = when (pageMotion(initialState.destination.route, targetState.destination.route, isPop)) {
    PageMotion.NONE -> ExitTransition.None
    PageMotion.FADE -> fadeOut(tween(FADE_DURATION))
    PageMotion.FORWARD -> fadeOut(tween(SLIDE_DURATION)) + slideOutHorizontally(
        tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
    ) { width -> -width / 20 }
    PageMotion.BACKWARD -> fadeOut(tween(SLIDE_DURATION)) + slideOutHorizontally(
        tween(SLIDE_DURATION, easing = FastOutSlowInEasing),
    ) { width -> width / 20 }
}
