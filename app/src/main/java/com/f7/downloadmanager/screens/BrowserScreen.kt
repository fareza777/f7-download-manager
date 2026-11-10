package com.f7.downloadmanager.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.core.data.db.BrowserBookmarkEntity
import com.f7.core.data.db.BrowserHistoryEntity
import com.f7.core.data.NewDownload
import com.f7.core.model.DownloadKind
import com.f7.core.model.F7Format
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7LogoMark
import com.f7.core.ui.components.F7Pill
import com.f7.core.ui.components.F7PrimaryButton
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.F7Application
import com.f7.downloadmanager.R
import com.f7.downloadmanager.clipboardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val app = context.applicationContext as F7Application
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appSettings by app.graph.settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.f7.core.model.AppSettings()
    )
    val bookmarks by app.graph.repository.bookmarks().collectAsStateWithLifecycle(emptyList())
    val history by app.graph.repository.browserHistory().collectAsStateWithLifecycle(emptyList())

    var address by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var isHttps by remember { mutableStateOf(true) }
    var pendingLoad by remember { mutableStateOf<String?>(null) }
    var isBookmarked by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryTab by remember { mutableIntStateOf(0) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun loadAddress(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return
        val url = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.contains(' ') || !trimmed.contains('.') ->
                searchUrl(trimmed, appSettings.browserSearchEngine)
            else -> "https://$trimmed"
        }
        currentUrl = url
        address = url
        isHttps = url.startsWith("https://", true)
        pendingLoad = url
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = appSettings.browserJavaScript
                domStorageEnabled = true
                databaseEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            CookieManager.getInstance().setAcceptCookie(appSettings.browserCookies)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVerticalScrollBarEnabled = true
        }
    }

    DisposableEffect(webView) {        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    LaunchedEffect(appSettings.browserJavaScript, appSettings.browserCookies, appSettings.browserDesktopMode) {
        webView.settings.javaScriptEnabled = appSettings.browserJavaScript
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(appSettings.browserCookies)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.settings.userAgentString = if (appSettings.browserDesktopMode) {
            DESKTOP_USER_AGENT
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
    }

    LaunchedEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
                    false
                } else {
                    // Never hand untrusted schemes (file:, intent:, javascript:) to the system.
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_invalid)) }
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress = 0.08f
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress = 1f
                currentUrl = url
                pageTitle = view.title
                address = url.orEmpty()
                isHttps = url?.startsWith("https://", true) == true
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                if (!url.isNullOrBlank()) {
                    scope.launch {
                        runCatching { app.graph.repository.addHistory(url, view.title) }
                        isBookmarked = runCatching { app.graph.repository.isBookmarked(url) }.getOrDefault(false)
                    }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                if (!url.isNullOrBlank()) {
                    currentUrl = url
                    address = url
                    isHttps = url.startsWith("https://", true)
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError?) {
                handler.cancel()
                scope.launch { snackbar.showSnackbar(context.getString(R.string.error_ssl)) }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress / 100f
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                pageTitle = title
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                return false
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            scope.launch {
                val cookies = if (appSettings.browserCookies) {
                    withContext(Dispatchers.Main) {
                        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                    }
                } else null
                val referer = webView.url
                val headers = buildMap {
                    if (!userAgent.isNullOrBlank()) put("User-Agent", userAgent)
                    if (!cookies.isNullOrBlank()) put("Cookie", cookies)
                    if (!referer.isNullOrBlank()) put("Referer", referer)
                }
                val fileName = F7Format.guessFileName(url, contentDisposition, mimeType)
                app.graph.repository.enqueue(
                    NewDownload(
                        url = url,
                        fileName = fileName,
                        mimeType = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" },
                        headers = headers,
                        kind = if (F7Format.isPlaylistUrl(url)) DownloadKind.HLS else DownloadKind.HTTP,
                    )
                )
                app.graph.startServiceSafely()
                snackbar.showSnackbar(context.getString(R.string.browser_download_started, fileName))
            }
        })
    }

    LaunchedEffect(pendingLoad) {
        val target = pendingLoad
        if (target != null) {
            webView.loadUrl(target)
            pendingLoad = null
        }
    }

    BackHandler(enabled = canGoBack) {
        if (webView.canGoBack()) webView.goBack()
    }

    F7ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    F7LogoMark(size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.browser_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = pageTitle?.take(48) ?: stringResource(R.string.browser_no_page),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { menuOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_f7_more_vert),
                                contentDescription = stringResource(R.string.action_more),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_library)) },
                                onClick = {
                                    menuOpen = false
                                    showLibrary = true
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_f7_bookmark), null) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (appSettings.browserDesktopMode) R.string.browser_desktop_off
                                            else R.string.browser_desktop
                                        )
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        app.graph.settingsStore.setBrowserDesktopMode(!appSettings.browserDesktopMode)
                                    }
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_f7_desktop_windows), null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_clear)) },
                                onClick = {
                                    menuOpen = false
                                    CookieManager.getInstance().removeAllCookies(null)
                                    WebStorage.getInstance().deleteAllData()
                                    webView.clearCache(true)
                                    webView.clearHistory()
                                    webView.loadUrl("about:blank")
                                    currentUrl = null
                                    address = ""
                                    pageTitle = null
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_cleared)) }
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_f7_delete_sweep), null) }
                            )
                            if (currentUrl != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.browser_share_page)) },
                                    onClick = {
                                        menuOpen = false
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                        }
                                        runCatching { context.startActivity(Intent.createChooser(share, null)) }
                                    },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_f7_share), null) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(
                            painterResource(if (isHttps) R.drawable.ic_f7_lock else R.drawable.ic_f7_public),
                            contentDescription = null,
                            tint = if (isHttps) LocalF7Colors.current.success else LocalF7Colors.current.warning,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp)
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.browser_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = {
                                loadAddress(address)
                                focusManager.clearFocus()
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        if (currentUrl != null) {
                            Icon(
                                painterResource(
                                    if (isBookmarked) R.drawable.ic_f7_bookmark
                                    else R.drawable.ic_f7_bookmark_add
                                ),
                                contentDescription = stringResource(R.string.browser_bookmarks),
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val url = currentUrl ?: return@clickable
                                        scope.launch {
                                            val now = app.graph.repository.toggleBookmark(url, pageTitle)
                                            isBookmarked = now
                                            snackbar.showSnackbar(
                                                context.getString(
                                                    if (now) R.string.browser_bookmark_added
                                                    else R.string.browser_bookmark_removed
                                                )
                                            )
                                        }
                                    }
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_f7_content_copy),
                            contentDescription = stringResource(R.string.action_paste),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    val clip = clipboardText(context)
                                    if (clip.isNullOrBlank()) {
                                        scope.launch { snackbar.showSnackbar(context.getString(R.string.add_clipboard_empty)) }
                                    } else {
                                        address = clip
                                    }
                                }
                                .padding(8.dp)
                                .size(18.dp)
                        )
                        Icon(
                            painterResource(R.drawable.ic_f7_arrow_forward),
                            contentDescription = stringResource(R.string.action_browse),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    loadAddress(address)
                                    focusManager.clearFocus()
                                }
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                }
                if (progress in 0.02f..0.98f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            if (currentUrl == null) {
                BrowserStartPage(
                    onOpen = { loadAddress(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            BrowserToolbar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBack = { if (webView.canGoBack()) webView.goBack() },
                onForward = { if (webView.canGoForward()) webView.goForward() },
                onReload = { webView.reload() },
                onDownloadPage = {
                    val target = webView.url ?: currentUrl
                    if (target == null) {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.browser_no_page)) }
                    } else {
                        scope.launch {
                            app.graph.repository.enqueue(
                                NewDownload(
                                    url = target,
                                    headers = buildMap {
                                        runCatching { CookieManager.getInstance().getCookie(target) }
                                            .getOrNull()?.let { put("Cookie", it) }
                                    },
                                    kind = DownloadKind.HTTP,
                                )
                            )
                            app.graph.startServiceSafely()
                            snackbar.showSnackbar(context.getString(R.string.add_started))
                        }
                    }
                },
                onShare = {
                    currentUrl?.let { url ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        runCatching { context.startActivity(Intent.createChooser(share, null)) }
                    }
                },
            )
        }
    }

    if (showLibrary) {
        BrowserLibrarySheet(
            bookmarks = bookmarks,
            history = history,
            initialTab = libraryTab,
            onDismiss = { showLibrary = false },
            onOpen = {
                showLibrary = false
                loadAddress(it)
            },
            onDeleteBookmark = {
                scope.launch { app.graph.repository.deleteBookmark(it) }
            },
            onDeleteHistory = {
                scope.launch { app.graph.repository.deleteHistory(it) }
            },
            onClearBookmarks = {
                scope.launch { app.graph.repository.clearBookmarks() }
            },
            onClearHistory = {
                scope.launch { app.graph.repository.clearHistory() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserLibrarySheet(
    bookmarks: List<BrowserBookmarkEntity>,
    history: List<BrowserHistoryEntity>,
    initialTab: Int,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearBookmarks: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(initialTab) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.browser_library),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        if (tab == 0) onClearBookmarks() else onClearHistory()
                    }
                ) {
                    Text(
                        stringResource(
                            if (tab == 0) R.string.browser_clear_bookmarks else R.string.browser_clear_history
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                F7ChoiceChip(
                    text = stringResource(R.string.browser_bookmarks),
                    selected = tab == 0,
                    onClick = { tab = 0 }
                )
                F7ChoiceChip(
                    text = stringResource(R.string.browser_history),
                    selected = tab == 1,
                    onClick = { tab = 1 }
                )
            }
            Spacer(Modifier.height(10.dp))
            if (tab == 0) {
                if (bookmarks.isEmpty()) {
                    LibraryEmpty(stringResource(R.string.browser_no_bookmarks))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        items(bookmarks, key = { it.id }) { item ->
                            LibraryRow(
                                icon = R.drawable.ic_f7_bookmark,
                                title = item.title?.takeIf { it.isNotBlank() } ?: item.url,
                                url = item.url,
                                onClick = { onOpen(item.url) },
                                onDelete = { onDeleteBookmark(item.url) }
                            )
                        }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    LibraryEmpty(stringResource(R.string.browser_no_history))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                        items(history, key = { it.id }) { item ->
                            LibraryRow(
                                icon = R.drawable.ic_f7_history,
                                title = item.title?.takeIf { it.isNotBlank() } ?: item.url,
                                url = item.url,
                                onClick = { onOpen(item.url) },
                                onDelete = { onDeleteHistory(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmpty(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 26.dp)
    )
}

@Composable
private fun LibraryRow(
    icon: Int,
    title: String,
    url: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painterResource(R.drawable.ic_f7_delete),
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDelete)
                .padding(8.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun BrowserStartPage(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        "Google" to "https://www.google.com",
        "Wikipedia" to "https://www.wikipedia.org",
        "Internet Archive" to "https://archive.org",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(118.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_f7_public),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.browser_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.browser_start_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        F7Card {
            Text(
                text = stringResource(R.string.browser_security),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.browser_cookie_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { (label, url) ->
                F7Pill(
                    text = label,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onOpen(url) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BrowserToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onDownloadPage: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarIcon(
                icon = R.drawable.ic_f7_arrow_back,
                label = stringResource(R.string.action_back),
                enabled = canGoBack,
                onClick = onBack
            )
            ToolbarIcon(
                icon = R.drawable.ic_f7_arrow_forward,
                label = stringResource(R.string.browser_forward),
                enabled = canGoForward,
                onClick = onForward
            )
            ToolbarIcon(
                icon = R.drawable.ic_f7_refresh,
                label = stringResource(R.string.browser_retry),
                enabled = true,
                onClick = onReload
            )
            ToolbarIcon(
                icon = R.drawable.ic_f7_download,
                label = stringResource(R.string.browser_download_page),
                enabled = true,
                onClick = onDownloadPage
            )
            ToolbarIcon(
                icon = R.drawable.ic_f7_share,
                label = stringResource(R.string.action_share),
                enabled = true,
                onClick = onShare
            )
        }
    }
}

@Composable
private fun ToolbarIcon(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(21.dp)
        )
    }
}







private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun searchUrl(query: String, engine: String): String {
    val encoded = android.net.Uri.encode(query)
    return when (engine) {
        "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
        "bing" -> "https://www.bing.com/search?q=$encoded"
        else -> "https://www.google.com/search?q=$encoded"
    }
}



