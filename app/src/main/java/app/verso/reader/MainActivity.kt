package app.verso.reader

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.verso.core.importer.DocumentSource
import app.verso.reader.intake.IntakeResult
import app.verso.reader.intake.IntentParser
import app.verso.reader.intake.SourceReader
import app.verso.reader.ui.home.HomeScreen
import app.verso.reader.ui.reader.ReaderPalette
import app.verso.reader.ui.reader.ReaderScreen
import app.verso.reader.ui.reader.paletteFor
import app.verso.reader.ui.settings.SettingsPanel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.factory((application as VersoApp).env)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val screen by viewModel.screen.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val recents by viewModel.recents.collectAsStateWithLifecycle()
            val books by viewModel.books.collectAsStateWithLifecycle()
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

            val isSystemDark = isSystemInDarkTheme()
            val palette = paletteFor(settings.theme, isSystemDark)

            var chromeVisible by rememberSaveable { mutableStateOf(true) }
            var settingsOpen by rememberSaveable { mutableStateOf(false) }

            // Manage status/navigation bar appearance and keep-screen-on
            DisposableEffect(palette.isDark, chromeVisible, screen, settings.keepScreenOn) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !palette.isDark
                insetsController.isAppearanceLightNavigationBars = !palette.isDark

                if (screen is Screen.Reading) {
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }

                    if (!chromeVisible) {
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }

                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // BackHandler logic
            BackHandler(enabled = true) {
                when {
                    settingsOpen -> settingsOpen = false
                    screen is Screen.Reading -> viewModel.closeReader()
                    screen is Screen.Importing -> viewModel.cancelImport()
                    screen is Screen.Failed -> viewModel.dismissError()
                    currentTab == MainTab.Library -> viewModel.selectTab(MainTab.Home)
                    else -> finish()
                }
            }

            // Material 3 theme wrapper
            val colorScheme = if (palette.isDark) {
                darkColorScheme(
                    background = palette.background,
                    onBackground = palette.text,
                    surface = palette.background,
                    onSurface = palette.text,
                    surfaceVariant = palette.codeBackground,
                    onSurfaceVariant = palette.muted,
                    primary = palette.link,
                    onPrimary = palette.background
                )
            } else {
                lightColorScheme(
                    background = palette.background,
                    onBackground = palette.text,
                    surface = palette.background,
                    onSurface = palette.text,
                    surfaceVariant = palette.codeBackground,
                    onSurfaceVariant = palette.muted,
                    primary = palette.link,
                    onPrimary = palette.background
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                val openDocLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) viewModel.acquire(null) {
                        when (val result = IntentParser.readUriContent(contentResolver, uri, null)) {
                            is IntakeResult.Source -> result.source
                            is IntakeResult.Failure -> throw app.verso.core.model.ImportError.Intake(result.message)
                            IntakeResult.Ignore -> throw app.verso.core.model.ImportError.Empty()
                        }
                    }
                }

                when (val curScreen = screen) {
                    is Screen.Home -> {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = palette.background,
                                    contentColor = palette.text
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == MainTab.Home,
                                        onClick = { viewModel.selectTab(MainTab.Home) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Filled.Home,
                                                contentDescription = "Home"
                                            )
                                        },
                                        label = { Text("Home") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.link,
                                            selectedTextColor = palette.link,
                                            unselectedIconColor = palette.muted,
                                            unselectedTextColor = palette.muted,
                                            indicatorColor = palette.codeBackground
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == MainTab.Library,
                                        onClick = { viewModel.selectTab(MainTab.Library) },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Filled.Book,
                                                contentDescription = "Library"
                                            )
                                        },
                                        label = { Text("Library") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.link,
                                            selectedTextColor = palette.link,
                                            unselectedIconColor = palette.muted,
                                            unselectedTextColor = palette.muted,
                                            indicatorColor = palette.codeBackground
                                        )
                                    )
                                }
                            }
                        ) { innerPadding ->
                            if (currentTab == MainTab.Home) {
                                HomeScreen(
                                    recents = recents,
                                    palette = palette,
                                    onOpen = { id -> viewModel.open(id) },
                                    onDelete = { id -> viewModel.delete(id) },
                                    onOpenFile = {
                                        openDocLauncher.launch(arrayOf("*/*"))
                                    },
                                    onPaste = {
                                        viewModel.acquire(null) {
                                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip
                                            val text = if (clip != null && clip.itemCount > 0) {
                                                clip.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()
                                            } else null
                                            if (text.isNullOrBlank()) throw app.verso.core.model.ImportError.Intake("Clipboard is empty.")
                                            val context = kotlinx.coroutines.currentCoroutineContext()
                                            DocumentSource.fromText(text) { context.ensureActive() }
                                        }
                                    },
                                    onNavigateToLibrary = { viewModel.selectTab(MainTab.Library) },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            } else {
                                app.verso.reader.ui.library.LibraryScreen(
                                    books = books,
                                    palette = palette,
                                    onOpenBook = { id -> viewModel.open(id) },
                                    onDeleteBook = { id -> viewModel.delete(id) },
                                    onImportFile = {
                                        openDocLauncher.launch(arrayOf("*/*"))
                                    },
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }

                    is Screen.Importing -> {
                        ImportingScreen(palette = palette, phase = curScreen.phase, onCancel = viewModel::cancelImport)
                    }

                    is Screen.Reading -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ReaderScreen(
                                document = curScreen.document,
                                settings = settings,
                                palette = palette,
                                initialBlock = curScreen.anchorBlock,
                                initialOffsetPx = curScreen.anchorOffsetPx,
                                chromeVisible = chromeVisible,
                                onToggleChrome = {
                                    if (settingsOpen) {
                                        settingsOpen = false
                                    } else {
                                        chromeVisible = !chromeVisible
                                    }
                                },
                                onAnchor = { block, offset ->
                                    viewModel.onAnchor(curScreen.id, block, offset)
                                },
                                onBack = { viewModel.closeReader() },
                                onOpenSettings = {
                                    settingsOpen = !settingsOpen
                                }
                            )

                            // Non-modal bottom settings panel
                            AnimatedVisibility(
                                visible = settingsOpen && chromeVisible,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it }),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                SettingsPanel(
                                    settings = settings,
                                    onChange = { newSettings ->
                                        viewModel.updateSettings(newSettings)
                                    }
                                )
                            }
                        }
                    }

                    is Screen.Failed -> {
                        ErrorScreen(
                            message = curScreen.message,
                            palette = palette,
                            onDismiss = { viewModel.dismissError() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_VIEW)) return
        viewModel.acquire(null) {
            when (val result = IntentParser.parse(intent, contentResolver)) {
                is IntakeResult.Source -> result.source
                is IntakeResult.Failure -> throw app.verso.core.model.ImportError.Intake(result.message)
                IntakeResult.Ignore -> throw app.verso.core.model.ImportError.Empty()
            }
        }
    }


}

@Composable
fun ImportingScreen(palette: ReaderPalette, modifier: Modifier = Modifier, phase: String = "Reading source", onCancel: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = palette.link,
                trackColor = palette.rule
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = phase,
                fontSize = 17.sp,
                color = palette.muted
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCancel) { Text("Cancel import") }
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    palette: ReaderPalette,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Can't open this",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = palette.text
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.link,
                    contentColor = palette.background
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("error_ok_button")
            ) {
                Text(
                    text = "OK",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
