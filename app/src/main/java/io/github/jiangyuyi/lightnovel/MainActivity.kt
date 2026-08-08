package io.github.jiangyuyi.lightnovel

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import io.github.jiangyuyi.lightnovel.core.ui.LightNovelTheme
import io.github.jiangyuyi.lightnovel.core.ui.viewModelFactory
import io.github.jiangyuyi.lightnovel.feature.app.AppViewModel
import io.github.jiangyuyi.lightnovel.feature.auth.AuthScreen
import io.github.jiangyuyi.lightnovel.feature.auth.AuthViewModel
import io.github.jiangyuyi.lightnovel.feature.account.HistoryScreen
import io.github.jiangyuyi.lightnovel.feature.account.HistoryViewModel
import io.github.jiangyuyi.lightnovel.feature.account.PublishingScreen
import io.github.jiangyuyi.lightnovel.feature.account.PublishingViewModel
import io.github.jiangyuyi.lightnovel.feature.account.SocialMode
import io.github.jiangyuyi.lightnovel.feature.account.SocialScreen
import io.github.jiangyuyi.lightnovel.feature.account.SocialViewModel
import io.github.jiangyuyi.lightnovel.feature.book.BookScreen
import io.github.jiangyuyi.lightnovel.feature.book.BookViewModel
import io.github.jiangyuyi.lightnovel.feature.bookshelf.BookshelfScreen
import io.github.jiangyuyi.lightnovel.feature.bookshelf.BookshelfViewModel
import io.github.jiangyuyi.lightnovel.feature.discover.DiscoverScreen
import io.github.jiangyuyi.lightnovel.feature.discover.DiscoverViewModel
import io.github.jiangyuyi.lightnovel.feature.profile.ProfileScreen
import io.github.jiangyuyi.lightnovel.feature.profile.ProfileViewModel
import io.github.jiangyuyi.lightnovel.feature.messages.DmThreadScreen
import io.github.jiangyuyi.lightnovel.feature.messages.DmThreadViewModel
import io.github.jiangyuyi.lightnovel.feature.messages.MessagesScreen
import io.github.jiangyuyi.lightnovel.feature.messages.MessagesViewModel
import io.github.jiangyuyi.lightnovel.core.model.UserSummary
import io.github.jiangyuyi.lightnovel.core.model.DmConversation
import io.github.jiangyuyi.lightnovel.feature.reader.ReaderScreen
import io.github.jiangyuyi.lightnovel.feature.reader.ReaderViewModel
import io.github.jiangyuyi.lightnovel.feature.search.SearchScreen
import io.github.jiangyuyi.lightnovel.feature.search.SearchViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LightNovelTheme { LightNovelApp() } }
    }
}

private object Routes {
    const val DISCOVER = "discover"
    const val BOOKSHELF = "bookshelf"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val AUTH = "auth"
    const val SOCIAL = "social/{mode}"
    const val HISTORY = "history"
    const val PUBLISHING = "publishing"
    const val MESSAGES = "messages"
    const val DM_THREAD = "dm/{peerUid}/{nickname}"
    const val BOOK = "book/{bookId}"
    const val READER = "reader/{bookId}/{chapterId}"

    fun book(id: Long) = "book/$id"
    fun reader(bookId: Long, chapterId: Long) = "reader/$bookId/$chapterId"
    fun social(mode: SocialMode) = "social/${mode.name.lowercase()}"
    fun dm(conversation: DmConversation) =
        "dm/${conversation.peerUid}/${Uri.encode(conversation.user.nickname)}"
}

private data class BottomDestination(val route: String, val label: String, val glyph: String)

private val bottomDestinations = listOf(
    BottomDestination(Routes.DISCOVER, "发现", "⌂"),
    BottomDestination(Routes.BOOKSHELF, "书架", "▤"),
    BottomDestination(Routes.SEARCH, "搜索", "⌕"),
    BottomDestination(Routes.PROFILE, "我的", "○"),
)

@Composable
private fun LightNovelApp() {
    val application = LocalContext.current.applicationContext as LightNovelApplication
    val container = application.container
    val navController = rememberNavController()
    val appViewModel: AppViewModel = viewModel(factory = viewModelFactory { AppViewModel(container.repository) })
    val session by appViewModel.session.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = bottomDestinations.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.openRoot(destination.route) },
                            icon = { Text(destination.glyph) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DISCOVER,
            modifier = Modifier.padding(if (currentRoute == Routes.READER) PaddingValues(0.dp) else padding),
        ) {
            composable(Routes.DISCOVER) {
                val vm: DiscoverViewModel = viewModel(factory = viewModelFactory { DiscoverViewModel(container.repository) })
                DiscoverScreen(vm) { navController.navigate(Routes.book(it)) }
            }
            composable(Routes.BOOKSHELF) {
                val vm: BookshelfViewModel = viewModel(factory = viewModelFactory { BookshelfViewModel(container.repository) })
                BookshelfScreen(
                    vm,
                    loggedIn = session.loggedIn,
                    onLogin = { navController.navigate(Routes.AUTH) },
                    onBook = { navController.navigate(Routes.book(it)) },
                )
            }
            composable(Routes.SEARCH) {
                val vm: SearchViewModel = viewModel(factory = viewModelFactory { SearchViewModel(container.repository) })
                SearchScreen(vm) { navController.navigate(Routes.book(it)) }
            }
            composable(Routes.PROFILE) {
                val vm: ProfileViewModel = viewModel(factory = viewModelFactory { ProfileViewModel(container.repository) })
                ProfileScreen(
                    viewModel = vm,
                    session = session,
                    onLogin = { navController.navigate(Routes.AUTH) },
                    onLogout = appViewModel::logout,
                    onBookshelf = { navController.openRoot(Routes.BOOKSHELF) },
                    onFollowing = { navController.navigate(Routes.social(SocialMode.FOLLOWING)) },
                    onFollowers = { navController.navigate(Routes.social(SocialMode.FOLLOWERS)) },
                    onHistory = { navController.navigate(Routes.HISTORY) },
                    onPublishing = { navController.navigate(Routes.PUBLISHING) },
                    onMessages = { navController.navigate(Routes.MESSAGES) },
                )
            }
            composable(
                Routes.SOCIAL,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) { entry ->
                val initialMode = if (entry.arguments?.getString("mode") == "followers") {
                    SocialMode.FOLLOWERS
                } else SocialMode.FOLLOWING
                val vm: SocialViewModel = viewModel(
                    key = "social-${initialMode.name}",
                    factory = viewModelFactory { SocialViewModel(container.repository, initialMode) },
                )
                SocialScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory { HistoryViewModel(container.repository) })
                HistoryScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { bookId, chapterId ->
                        navController.navigate(chapterId?.let { Routes.reader(bookId, it) } ?: Routes.book(bookId))
                    },
                )
            }
            composable(Routes.PUBLISHING) {
                val vm: PublishingViewModel = viewModel(factory = viewModelFactory { PublishingViewModel(container.repository) })
                PublishingScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onBook = { navController.navigate(Routes.book(it)) },
                )
            }
            composable(Routes.MESSAGES) {
                val vm: MessagesViewModel = viewModel(factory = viewModelFactory {
                    MessagesViewModel(container.repository)
                })
                MessagesScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onConversation = { navController.navigate(Routes.dm(it)) },
                    onTarget = { bookId, chapterId ->
                        navController.navigate(chapterId?.let { Routes.reader(bookId, it) } ?: Routes.book(bookId))
                    },
                )
            }
            composable(
                Routes.DM_THREAD,
                arguments = listOf(
                    navArgument("peerUid") { type = NavType.LongType },
                    navArgument("nickname") { type = NavType.StringType },
                ),
            ) { entry ->
                val peerUid = entry.arguments?.getLong("peerUid") ?: return@composable
                val nickname = entry.arguments?.getString("nickname").orEmpty().ifBlank { "用户$peerUid" }
                val peer = UserSummary(peerUid, nickname)
                val vm: DmThreadViewModel = viewModel(
                    key = "dm-$peerUid",
                    factory = viewModelFactory { DmThreadViewModel(peer, container.repository) },
                )
                DmThreadScreen(peer, vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.AUTH) {
                val vm: AuthViewModel = viewModel(factory = viewModelFactory { AuthViewModel(container.repository) })
                AuthScreen(vm, onBack = { navController.popBackStack() }, onCompleted = { navController.popBackStack() })
            }
            composable(
                Routes.BOOK,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                val vm: BookViewModel = viewModel(
                    key = "book-$bookId",
                    factory = viewModelFactory { BookViewModel(bookId, container.repository) },
                )
                BookScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onLogin = { navController.navigate(Routes.AUTH) },
                    onBook = { navController.navigate(Routes.book(it)) },
                    onRead = { navController.navigate(Routes.reader(bookId, it)) },
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("chapterId") { type = NavType.LongType },
                ),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
                val vm: ReaderViewModel = viewModel(
                    key = "reader-$bookId-$chapterId",
                    factory = viewModelFactory {
                        ReaderViewModel(bookId, chapterId, container.repository, container.readerPreferences)
                    },
                )
                ReaderScreen(
                    vm,
                    onBack = { navController.popBackStack() },
                    onCatalog = { navController.navigate(Routes.book(bookId)) },
                )
            }
        }
    }
}

private fun NavHostController.openRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
