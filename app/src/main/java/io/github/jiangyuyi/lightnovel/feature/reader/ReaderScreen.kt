package io.github.jiangyuyi.lightnovel.feature.reader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import io.github.jiangyuyi.lightnovel.core.model.ReaderFont
import io.github.jiangyuyi.lightnovel.core.model.ReaderChineseScript
import io.github.jiangyuyi.lightnovel.core.model.ReaderMode
import io.github.jiangyuyi.lightnovel.core.model.ReaderPreferences
import io.github.jiangyuyi.lightnovel.core.model.ReaderTheme
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit, onCatalog: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = state.preferences.readerColors()
    val safeTopPadding = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val chapterId = state.chapter?.chapter?.id
    val sourceContent = remember(state.chapter) {
        val chapter = state.chapter
        ReaderDisplayContent(
            bookTitle = chapter?.bookTitle.orEmpty(),
            chapterTitle = chapter?.chapter?.title.orEmpty(),
            blocks = if (chapter == null) emptyList() else buildList {
                add(ReaderBlock.Heading(chapter.chapter.title))
                addAll(ReaderContentParser.parse(chapter.bodyHtml, chapter.bodyText))
            },
        )
    }
    var displayContent by remember(chapterId) { mutableStateOf(sourceContent) }
    var converting by remember(chapterId) { mutableStateOf(false) }
    val blocks = displayContent.blocks

    LaunchedEffect(sourceContent, state.preferences.chineseScript) {
        converting = true
        try {
            displayContent = ReaderChineseConverter.convert(sourceContent, state.preferences.chineseScript)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            displayContent = sourceContent
            Toast.makeText(context, "简繁转换失败，已显示原文，请重新选择后重试", Toast.LENGTH_LONG).show()
        } finally {
            converting = false
        }
    }
    var anchorBlock by rememberSaveable(state.chapter?.chapter?.id) {
        mutableIntStateOf(state.restoredParagraph.coerceAtLeast(0))
    }
    var readerPosition by remember(chapterId, state.preferences.mode) {
        mutableStateOf(ReaderPosition(unit = if (state.preferences.mode == ReaderMode.PAGED) "页" else "段"))
    }
    var jumpRequest by remember(chapterId, state.preferences.mode) {
        mutableStateOf<ReaderJumpRequest?>(null)
    }
    var jumpRequestToken by remember { mutableIntStateOf(0) }
    var jumpDialogVisible by rememberSaveable(chapterId, state.preferences.mode) { mutableStateOf(false) }
    var pendingLink by remember { mutableStateOf<ReaderLinkTarget?>(null) }
    var webUnlockOpened by remember { mutableStateOf(false) }
    val openReaderLink: (ReaderBlock.Link) -> Unit = { link ->
        val target = ReaderLinkPolicy.classify(link.url, link.text)
        when (target?.kind) {
            ReaderLinkKind.INTERNAL_WEB -> context.openWebUrl(target.url)
            ReaderLinkKind.DIRECT_DOWNLOAD,
            ReaderLinkKind.EXTERNAL_WEB,
            -> pendingLink = target
            null -> Toast.makeText(context, "该链接不受支持", Toast.LENGTH_SHORT).show()
        }
    }

    ImmersiveReaderEffect(darkBackground = state.preferences.theme == ReaderTheme.DARK)

    LaunchedEffect(state.chapter?.chapter?.id, state.restoredParagraph) {
        anchorBlock = state.restoredParagraph.coerceAtLeast(0)
    }

    DisposableEffect(lifecycleOwner, webUnlockOpened) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && webUnlockOpened) {
                webUnlockOpened = false
                viewModel.refreshAfterWebUnlock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
        ) {
            if (state.chapter != null) {
                ReaderChapterHeader(displayContent.bookTitle, displayContent.chapterTitle, colors.text)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(chapterId) {
                    when {
                        state.loading && state.chapter == null -> LoadingPane(Modifier.align(Alignment.Center))
                        state.error != null -> ErrorPane(
                            message = state.error!!,
                            modifier = Modifier.align(Alignment.Center),
                            onRetry = viewModel::retry,
                        )
                        state.preferences.mode == ReaderMode.PAGED -> PagedReader(
                            blocks = blocks,
                            preferences = state.preferences,
                            colors = colors,
                            anchorBlock = anchorBlock,
                            onAnchorChanged = { anchorBlock = it },
                            onProgress = { index -> viewModel.saveProgress(index, blocks.size) },
                            onToggleControls = viewModel::toggleControls,
                            onPreviousChapter = viewModel::previous,
                            onNextChapter = viewModel::next,
                            hasPreviousChapter = state.chapter?.previousChapterId != null,
                            hasNextChapter = state.chapter?.nextChapterId != null,
                            jumpRequest = jumpRequest,
                            onJumpConsumed = { consumed -> if (jumpRequest == consumed) jumpRequest = null },
                            onPositionChanged = { current, total -> readerPosition = ReaderPosition(current, total, "页") },
                            onLink = openReaderLink,
                        )
                        else -> ScrollingReader(
                            blocks = blocks,
                            preferences = state.preferences,
                            colors = colors,
                            anchorBlock = anchorBlock,
                            onAnchorChanged = { anchorBlock = it },
                            onProgress = { index -> viewModel.saveProgress(index, blocks.size) },
                            onToggleControls = viewModel::toggleControls,
                            jumpRequest = jumpRequest,
                            onJumpConsumed = { consumed -> if (jumpRequest == consumed) jumpRequest = null },
                            onPositionChanged = { current, total -> readerPosition = ReaderPosition(current, total, "段") },
                            onLink = openReaderLink,
                        )
                    }
                }
            }
            if (state.chapter != null && state.error == null) {
                ReaderStatusFooter(
                    current = readerPosition.current,
                    total = readerPosition.total,
                    paged = state.preferences.mode == ReaderMode.PAGED,
                    textColor = colors.text,
                    horizontalPadding = state.preferences.horizontalPadding,
                )
            }
        }

        if (state.refreshing || converting) {
            LinearProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = safeTopPadding),
            )
        }

        if (state.controlsVisible && !state.loading && state.error == null) {
            ReaderControls(
                bookTitle = displayContent.bookTitle.ifBlank { "阅读" },
                chapterTitle = displayContent.chapterTitle,
                colors = colors,
                onBack = onBack,
                onCatalog = onCatalog,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onSettings = { viewModel.showSettings(true) },
                previousEnabled = state.chapter?.previousChapterId != null,
                nextEnabled = state.chapter?.nextChapterId != null,
                position = readerPosition,
                onQuickJump = { position ->
                    jumpRequestToken += 1
                    jumpRequest = ReaderJumpRequest(position, jumpRequestToken)
                },
                onShowJumpDialog = { jumpDialogVisible = true },
            )
        }

        val chapter = state.chapter?.chapter
        if (chapter?.accessType.equals("coin", ignoreCase = true) && chapter?.unlocked != true && !state.unlockPromptVisible) {
            Button(
                onClick = { viewModel.showUnlockPrompt(true) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            ) { Text("解锁本章") }
        }
    }

    if (state.settingsVisible) {
        ReaderSettingsDialog(
            preferences = state.preferences,
            onChange = { value -> viewModel.updatePreferences { value } },
            onDismiss = { viewModel.showSettings(false) },
        )
    }

    if (jumpDialogVisible) {
        ReaderJumpDialog(
            position = readerPosition,
            onJump = { position ->
                jumpRequestToken += 1
                jumpRequest = ReaderJumpRequest(position, jumpRequestToken)
                jumpDialogVisible = false
            },
            onDismiss = { jumpDialogVisible = false },
        )
    }


    val lockedChapter = state.chapter?.chapter
    if (state.unlockPromptVisible && lockedChapter != null) {
        AlertDialog(
            onDismissRequest = { viewModel.showUnlockPrompt(false) },
            title = { Text("解锁章节") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本章需要 ${lockedChapter.coinPrice.coerceAtLeast(0)} 轻币解锁。")
                    when {
                        !state.loggedIn -> Text("App 当前未登录。网页可能会要求先登录轻之国度。")
                        state.coinBalanceLoading -> Text("正在查询轻币余额…")
                        state.coinBalance != null -> Text("当前余额：${state.coinBalance} 轻币")
                        else -> Text("暂未取得余额，可刷新后再继续。")
                    }
                    Text("支付将在轻之国度官方网页完成；返回 App 后会自动刷新章节和余额。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        webUnlockOpened = true
                        viewModel.showUnlockPrompt(false)
                        context.openWebUrl("https://www.lightnovel.fun/reader/${lockedChapter.bookId}/${lockedChapter.id}")
                    },
                ) { Text(if (state.loggedIn) "前往网页解锁" else "登录并解锁") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.refreshCoinBalance(force = true) }, enabled = !state.coinBalanceLoading) { Text("刷新余额") }
                    TextButton(onClick = { viewModel.showUnlockPrompt(false) }) { Text("取消") }
                }
            },
        )
    }

    pendingLink?.let { target ->
        ReaderLinkDialog(
            target = target,
            onDismiss = { pendingLink = null },
            onConfirm = {
                when (target.kind) {
                    ReaderLinkKind.DIRECT_DOWNLOAD -> context.enqueueDownload(target)
                    ReaderLinkKind.EXTERNAL_WEB,
                    ReaderLinkKind.INTERNAL_WEB,
                    -> context.openWebUrl(target.url)
                }
                pendingLink = null
            },
        )
    }
}

@Composable
private fun PagedReader(
    blocks: List<ReaderBlock>,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    anchorBlock: Int,
    onAnchorChanged: (Int) -> Unit,
    onProgress: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    hasPreviousChapter: Boolean,
    hasNextChapter: Boolean,
    jumpRequest: ReaderJumpRequest?,
    onJumpConsumed: (ReaderJumpRequest) -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
    onLink: (ReaderBlock.Link) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val paragraphStyle = preferences.paragraphStyle(colors.text)
        val headingStyle = preferences.headingStyle(colors.text)
        val horizontalPadding = preferences.horizontalPadding.dp
        val pageTopPadding = 8.dp
        val pageBottomPadding = 12.dp
        val pageWidthPx = with(density) { (maxWidth - horizontalPadding * 2).roundToPx().coerceAtLeast(1) }
        val pageHeightPx = with(density) {
            (maxHeight - pageTopPadding - pageBottomPadding).roundToPx().coerceAtLeast(1)
        }
        val spacingPx = with(density) { 14.dp.roundToPx() }
        val pages = remember(blocks, preferences.font, preferences.fontSize, preferences.lineHeight, preferences.horizontalPadding, pageWidthPx, pageHeightPx) {
            paginateReaderBlocks(
                blocks = blocks,
                textMeasurer = textMeasurer,
                paragraphStyle = paragraphStyle,
                headingStyle = headingStyle,
                density = density,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx,
                spacingPx = spacingPx,
            )
        }
        val pagerState = rememberPagerState {
            pages.size.coerceAtLeast(1)
        }
        var positionedPages by remember { mutableStateOf<List<ReaderPage>?>(null) }
        var turnRequest by remember { mutableStateOf<ReaderTurnRequest?>(null) }
        var turnRequestToken by remember { mutableIntStateOf(0) }

        LaunchedEffect(pages) {
            val target = readerPageForAnchor(pages, anchorBlock, pagerState.currentPage)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            positionedPages = pages
        }
        LaunchedEffect(jumpRequest?.token, pages.size) {
            val request = jumpRequest ?: return@LaunchedEffect
            pagerState.animateScrollToPage(request.position.coerceIn(1, pages.size) - 1)
            onJumpConsumed(request)
        }
        LaunchedEffect(pagerState, pages) {
            snapshotFlow { pagerState.currentPage.takeIf { positionedPages === pages } }
                .distinctUntilChanged()
                .collect { pageIndex ->
                    if (pageIndex == null) return@collect
                    pages.getOrNull(pageIndex)?.firstBlockIndex?.let {
                        onAnchorChanged(it)
                        onProgress(it)
                        onPositionChanged(pageIndex + 1, pages.size.coerceAtLeast(1))
                    }
                }
        }
        // Chapter changes only follow an explicit gesture. Re-pagination (including
        // script conversion) can shrink the page count and must not advance chapters.
        LaunchedEffect(turnRequest?.token) {
            val request = turnRequest ?: return@LaunchedEffect
            when (request.direction) {
                ReaderTurnDirection.PREVIOUS -> when {
                    pagerState.currentPage > 0 -> pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    hasPreviousChapter -> onPreviousChapter()
                }
                ReaderTurnDirection.NEXT -> when {
                    pagerState.currentPage < pages.lastIndex -> pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    hasNextChapter -> onNextChapter()
                }
            }
            if (turnRequest == request) turnRequest = null
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages.size, hasPreviousChapter, hasNextChapter) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val start = down.position
                        var releasedX: Float? = null
                        var releasedY: Float? = null
                        var childConsumed = false
                        while (releasedX == null) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            childConsumed = childConsumed || change.isConsumed
                            if (!change.pressed) {
                                releasedX = change.position.x
                                releasedY = change.position.y
                            }
                        }
                        if (childConsumed) return@awaitEachGesture
                        val endX = releasedX ?: return@awaitEachGesture
                        val endY = releasedY ?: return@awaitEachGesture
                        val deltaX = endX - start.x
                        val deltaY = endY - start.y
                        val swipeThreshold = size.width * 0.10f
                        val isHorizontalSwipe = abs(deltaX) >= swipeThreshold && abs(deltaX) > abs(deltaY)

                        fun requestTurn(direction: ReaderTurnDirection) {
                            turnRequestToken += 1
                            turnRequest = ReaderTurnRequest(direction, turnRequestToken)
                        }

                        when {
                            isHorizontalSwipe && deltaX > 0 -> requestTurn(ReaderTurnDirection.PREVIOUS)
                            isHorizontalSwipe && deltaX < 0 -> requestTurn(ReaderTurnDirection.NEXT)
                            abs(deltaX) <= viewConfiguration.touchSlop && abs(deltaY) <= viewConfiguration.touchSlop -> {
                                when (endX / size.width.toFloat().coerceAtLeast(1f)) {
                                    in 0f..0.30f -> requestTurn(ReaderTurnDirection.PREVIOUS)
                                    in 0.70f..1f -> requestTurn(ReaderTurnDirection.NEXT)
                                    else -> onToggleControls()
                                }
                            }
                        }
                    }
                }
                .padding(top = pageTopPadding, bottom = pageBottomPadding),
        ) { pageIndex ->
            if (pageIndex < pages.size) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    pages.getOrNull(pageIndex)?.elements.orEmpty().forEach { element ->
                        when (element) {
                            is ReaderPageElement.Text -> ReaderTextElement(element, preferences, colors, onLink)
                            is ReaderPageElement.Illustration -> ReaderIllustration(
                                block = element.block,
                                modifier = Modifier.fillMaxWidth().height(with(density) { element.heightPx.toDp() }),
                                colors = colors,
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在进入下一章…", color = colors.text.copy(alpha = 0.72f))
                }
            }
        }

    }
}

@Composable
private fun ScrollingReader(
    blocks: List<ReaderBlock>,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    anchorBlock: Int,
    onAnchorChanged: (Int) -> Unit,
    onProgress: (Int) -> Unit,
    onToggleControls: () -> Unit,
    jumpRequest: ReaderJumpRequest?,
    onJumpConsumed: (ReaderJumpRequest) -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
    onLink: (ReaderBlock.Link) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(blocks) {
        if (blocks.isNotEmpty() && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(anchorBlock.coerceIn(0, blocks.lastIndex))
        }
    }
    LaunchedEffect(jumpRequest?.token, blocks.size) {
        val request = jumpRequest ?: return@LaunchedEffect
        if (blocks.isNotEmpty()) {
            listState.animateScrollToItem(request.position.coerceIn(1, blocks.size) - 1)
        }
        onJumpConsumed(request)
    }
    LaunchedEffect(listState, blocks.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                onAnchorChanged(it)
                onProgress(it)
                onPositionChanged(it + 1, blocks.size.coerceAtLeast(1))
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onToggleControls) {
                detectTapGestures { position ->
                    val horizontalFraction = position.x / size.width.toFloat().coerceAtLeast(1f)
                    val verticalFraction = position.y / size.height.toFloat().coerceAtLeast(1f)
                    if (horizontalFraction in 0.30f..0.70f && verticalFraction in 0.25f..0.75f) {
                        onToggleControls()
                    }
                }
            },
        contentPadding = PaddingValues(
            start = preferences.horizontalPadding.dp,
            end = preferences.horizontalPadding.dp,
            top = 8.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(blocks) { _, block ->
            when (block) {
                is ReaderBlock.Heading -> Text(block.text, style = preferences.headingStyle(colors.text))
                is ReaderBlock.Paragraph -> Text(
                    block.text,
                    style = preferences.paragraphStyle(colors.text).copy(
                        textIndent = if (block.firstLineIndent) TextIndent(firstLine = preferences.fontSize.sp * 2) else TextIndent.None,
                    ),
                )
                is ReaderBlock.Illustration -> ReaderIllustration(block, Modifier.fillMaxWidth(), colors)
                is ReaderBlock.Link -> ReaderLinkText(block, preferences, colors, onLink)
            }
        }
    }
}

@Composable
private fun ReaderTextElement(
    element: ReaderPageElement.Text,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    onLink: (ReaderBlock.Link) -> Unit,
) {
    val style = if (element.heading) preferences.headingStyle(colors.text) else preferences.paragraphStyle(colors.text)
    val linkUrl = element.linkUrl
    Text(
        text = element.text,
        color = if (linkUrl != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
        textDecoration = if (linkUrl != null) TextDecoration.Underline else null,
        modifier = if (linkUrl != null) Modifier.clickable { onLink(ReaderBlock.Link(element.text, linkUrl)) } else Modifier,
        style = style.copy(
            textIndent = if (element.firstLineIndent) TextIndent(firstLine = preferences.fontSize.sp * 2) else TextIndent.None,
        ),
    )
}

@Composable
private fun ReaderLinkText(
    block: ReaderBlock.Link,
    preferences: ReaderPreferences,
    colors: ReaderColors,
    onLink: (ReaderBlock.Link) -> Unit,
) {
    Text(
        text = block.text,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        style = preferences.paragraphStyle(colors.text),
        modifier = Modifier.fillMaxWidth().clickable { onLink(block) }.padding(vertical = 4.dp),
    )
}

@Composable
private fun ReaderIllustration(block: ReaderBlock.Illustration, modifier: Modifier, colors: ReaderColors) {
    SubcomposeAsyncImage(
        model = block.url,
        contentDescription = "正文插图",
        contentScale = ContentScale.Fit,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize().background(colors.text.copy(alpha = 0.04f)), contentAlignment = Alignment.Center) {
                LinearProgressIndicator(Modifier.fillMaxWidth(0.45f))
            }
        },
        error = {
            Box(Modifier.fillMaxSize().background(colors.text.copy(alpha = 0.04f)), contentAlignment = Alignment.Center) {
                Text("插图加载失败", color = colors.text.copy(alpha = 0.7f))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.ReaderControls(
    bookTitle: String,
    chapterTitle: String,
    colors: ReaderColors,
    onBack: () -> Unit,
    onCatalog: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    position: ReaderPosition,
    onQuickJump: (Int) -> Unit,
    onShowJumpDialog: () -> Unit,
) {
    var sliderValue by remember(position.current, position.total) {
        mutableFloatStateOf(position.current.toFloat())
    }
    TopAppBar(
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        title = {
            Column {
                Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chapterTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
        actions = { TextButton(onClick = onCatalog) { Text("目录") } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background.copy(alpha = 0.97f),
            titleContentColor = colors.text,
            navigationIconContentColor = colors.text,
            actionIconContentColor = colors.text,
        ),
    )
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        color = colors.background.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = sliderValue.coerceIn(1f, position.total.coerceAtLeast(2).toFloat()),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onQuickJump(sliderValue.roundToInt()) },
                    valueRange = 1f..position.total.coerceAtLeast(2).toFloat(),
                    enabled = position.total > 1,
                    colors = readerSliderColors(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onShowJumpDialog) {
                    Text("${position.current}/${position.total}${position.unit}", color = colors.text)
                }
            }
            HorizontalDivider(color = colors.text.copy(alpha = 0.12f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious, enabled = previousEnabled) { Text("上一章") }
                Button(onClick = onSettings) { Text("阅读设置") }
                TextButton(onClick = onNext, enabled = nextEnabled) { Text("下一章") }
            }
        }
    }
}

@Composable
private fun ReaderJumpDialog(
    position: ReaderPosition,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember(position.current, position.total) { mutableStateOf(position.current.toString()) }
    val target = input.toIntOrNull()?.takeIf { it in 1..position.total.coerceAtLeast(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (position.unit == "页") "跳转页码" else "快速定位") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter(Char::isDigit).take(6) },
                    label = { Text("${position.unit}码") },
                    supportingText = { Text("可输入 1 到 ${position.total}") },
                    isError = input.isNotEmpty() && target == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onJump) }, enabled = target != null) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ReaderSettingsDialog(
    preferences: ReaderPreferences,
    onChange: (ReaderPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        title = { Text("阅读设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("简繁转换")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderChineseScript.entries.forEach { script ->
                        ReaderOptionChip(
                            selected = preferences.chineseScript == script,
                            onClick = { onChange(preferences.copy(chineseScript = script)) },
                            label = script.label,
                        )
                    }
                }
                Text("仅转换阅读文字，原文与链接保持不变", style = MaterialTheme.typography.bodySmall)
                Text("翻页方式")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderMode.entries.forEach { mode ->
                        ReaderOptionChip(
                            selected = preferences.mode == mode,
                            onClick = { onChange(preferences.copy(mode = mode)) },
                            label = mode.label,
                        )
                    }
                }
                Text("字体")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderFont.entries.forEach { font ->
                        ReaderOptionChip(
                            selected = preferences.font == font,
                            onClick = { onChange(preferences.copy(font = font)) },
                            label = font.label,
                        )
                    }
                }
                Text("字号 ${preferences.fontSize.toInt()}")
                Slider(
                    value = preferences.fontSize,
                    onValueChange = { onChange(preferences.copy(fontSize = it)) },
                    valueRange = 14f..32f,
                    steps = 17,
                    colors = readerSliderColors(),
                )
                Text("行高 ${"%.1f".format(preferences.lineHeight)}")
                Slider(
                    value = preferences.lineHeight,
                    onValueChange = { onChange(preferences.copy(lineHeight = it)) },
                    valueRange = 1.2f..2.2f,
                    steps = 9,
                    colors = readerSliderColors(),
                )
                Text("页边距 ${preferences.horizontalPadding}")
                Slider(
                    value = preferences.horizontalPadding.toFloat(),
                    onValueChange = { onChange(preferences.copy(horizontalPadding = it.toInt())) },
                    valueRange = 12f..40f,
                    steps = 13,
                    colors = readerSliderColors(),
                )
                Text("背景")
                ReaderTheme.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { theme ->
                            ReaderOptionChip(
                                selected = preferences.theme == theme,
                                onClick = { onChange(preferences.copy(theme = theme)) },
                                label = theme.label,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ReaderOptionChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        leadingIcon = if (selected) {
            { Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun ReaderLinkDialog(
    target: ReaderLinkTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val directDownload = target.kind == ReaderLinkKind.DIRECT_DOWNLOAD
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (directDownload) "下载文件" else "打开站外链接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (directDownload) Text("文件将保存到系统“下载”目录：${target.suggestedFileName}")
                else Text("即将离开 App，交由浏览器或对应网盘应用打开。")
                Text("来源：${target.host}", style = MaterialTheme.typography.bodySmall)
                if (target.url.startsWith("http://")) {
                    Text("该地址未使用 HTTPS，请确认来源可信。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (directDownload) "开始下载" else "打开") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun Context.openWebUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(this, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.enqueueDownload(target: ReaderLinkTarget) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(target.url))
            .setTitle(target.suggestedFileName)
            .setDescription("来自 ${target.host}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, target.suggestedFileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .addRequestHeader("Referer", "https://www.lightnovel.fun/")
        getSystemService(Context.DOWNLOAD_SERVICE).let { it as DownloadManager }.enqueue(request)
    }.onSuccess {
        Toast.makeText(this, "已加入下载任务", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(this, "无法开始下载：${it.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun readerSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
)

@Composable
private fun ImmersiveReaderEffect(darkBackground: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(view, darkBackground, lifecycleOwner) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.isAppearanceLightStatusBars = !darkBackground
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousLightStatusBars?.let { controller?.isAppearanceLightStatusBars = it }
            previousBehavior?.let { controller?.systemBarsBehavior = it }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class ReaderPosition(
    val current: Int = 1,
    val total: Int = 1,
    val unit: String,
)

private data class ReaderJumpRequest(val position: Int, val token: Int)

private enum class ReaderTurnDirection { PREVIOUS, NEXT }

private data class ReaderTurnRequest(val direction: ReaderTurnDirection, val token: Int)

private data class ReaderColors(val background: Color, val text: Color)

@Composable
private fun ReaderPreferences.readerColors(): ReaderColors = when (theme) {
    ReaderTheme.WHITE -> ReaderColors(Color(0xFFFFFBFF), Color(0xFF211A1C))
    ReaderTheme.SEPIA -> ReaderColors(Color(0xFFF7EED9), Color(0xFF3A3025))
    ReaderTheme.GREEN -> ReaderColors(Color(0xFFDDEBDD), Color(0xFF233128))
    ReaderTheme.DARK -> ReaderColors(Color(0xFF171416), Color(0xFFE8E0E2))
}

private fun ReaderPreferences.paragraphStyle(color: Color) = TextStyle(
    color = color,
    fontFamily = font.family(),
    fontSize = fontSize.sp,
    lineHeight = (fontSize * lineHeight).sp,
)

private fun ReaderPreferences.headingStyle(color: Color) = TextStyle(
    color = color,
    fontFamily = font.family(),
    fontSize = (fontSize + 5).sp,
    lineHeight = ((fontSize + 5) * lineHeight).sp,
)

private fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.MONO -> FontFamily.Monospace
}
