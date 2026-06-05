package dev.jelly.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.jelly.capture.jellySource

/**
 * Multi-page sample, mirroring `jelly-swift`'s `ContentView.swift`. The
 * activity has zero Jelly code — the toolbar comes from
 * [SampleApp.onCreate]'s `Jelly.install(this)` call. Navigation lives entirely
 * inside this Compose tree so the toolbar (attached to the window decor view)
 * stays put as the user pushes / pops between demo pages.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 (targetSdk 35) enforces edge-to-edge: the system bars go
        // transparent and content draws behind them. Opt in explicitly so the
        // status-bar icons adapt to our surface, then each screen consumes the
        // insets it needs (Scaffold does this for the toolbar'd pages; the Home
        // list folds them into its contentPadding).
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleNav()
                }
            }
        }
    }
}

private object Route {
    const val Home = "home"
    const val Login = "login"
    const val Profile = "profile"
    const val Settings = "settings"
    const val Catalogue = "catalogue"
}

@Composable
private fun SampleNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.Home) {
        composable(Route.Home) { HomePage(nav) }
        composable(Route.Login) { ScaffoldedPage(nav) { LoginPage() } }
        composable(Route.Profile) { ScaffoldedPage(nav) { ProfilePage() } }
        composable(Route.Settings) { ScaffoldedPage(nav, title = "Settings") { AppSettingsPage() } }
        composable(Route.Catalogue) { ScaffoldedPage(nav, title = "Catalogue") { CataloguePage() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldedPage(
    nav: NavHostController,
    title: String = "",
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

// ─── Home ───────────────────────────────────────────────────────────────

private enum class Demo(
    val route: String,
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
) {
    Login(Route.Login, Icons.Default.AccountCircle, Color(0xFF2F88FF),
        "Login form", "Text fields, secure entry & buttons"),
    Profile(Route.Profile, Icons.Default.Person, Color(0xFF9B59E0),
        "Profile", "Editable fields and toggles"),
    Settings(Route.Settings, Icons.Default.Settings, Color(0xFFFF9F0A),
        "Settings", "Steppers, pickers & switches"),
    Catalogue(Route.Catalogue, Icons.Default.GridView, Color(0xFFFF3B82),
        "Catalogue", "A deep scrolling list"),
}

@Composable
private fun HomePage(nav: NavHostController) {
    // Live state for the "Native elements" section.
    var pushEnabled by remember { mutableStateOf(true) }
    var volume by remember { mutableStateOf(0.6f) }
    var copies by remember { mutableStateOf(2) }
    var mode by remember { mutableStateOf(0) }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val heroAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(380), label = "hero-alpha")
    val heroOffset by animateDpAsState(if (appeared) 0.dp else 10.dp, spring(0.85f, 350f), label = "hero-offset")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .jellySource("MainActivity.kt", 1),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            HeroHeader(
                modifier = Modifier
                    .alpha(heroAlpha)
                    .offset(y = heroOffset),
            )
        }

        item {
            Section("Demo pages") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Demo.entries.forEach { demo ->
                        DemoRow(demo) { nav.navigate(demo.route) }
                    }
                }
            }
        }

        item {
            Section(
                "Native elements",
                footer = "Standard Material controls. Captured via the accessibility / semantics probe.",
            ) {
                SectionCard {
                    ToggleRow("Push notifications", pushEnabled, { pushEnabled = it })
                    Divider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Volume", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = volume,
                            onValueChange = { volume = it },
                            modifier = Modifier.semantics { testTag = "native-slider" },
                        )
                    }
                    Divider()
                    StepperRow("Copies", copies, range = 1..10, onChange = { copies = it })
                    Divider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        val labels = listOf("Light", "Dark", "Auto")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().semantics { testTag = "native-picker" }) {
                            labels.forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = mode == i,
                                    onClick = { mode = i },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                                ) { Text(label) }
                            }
                        }
                    }
                    Divider()
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Native button" },
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("Native button")
                        }
                    }
                }
            }
        }

        item {
            Section(
                "Custom elements",
                footer = "Shape- and Canvas-drawn views with no native control semantics. " +
                    "Captured via the view-hierarchy probe.",
            ) {
                SectionCard {
                    Box(modifier = Modifier.padding(16.dp)) { CustomChips() }
                    Divider()
                    Box(modifier = Modifier.padding(16.dp)) { CustomRing(progress = 0.72f) }
                    Divider()
                    Box(modifier = Modifier.padding(16.dp)) { CustomBars() }
                }
            }
        }

        item {
            Section("How to use") {
                SectionCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StepRow(1, "Tap the location pin in the bottom-right corner.")
                        StepRow(2, "Tap it again to enter annotate mode.")
                        StepRow(3, "Long-press any element to capture it.")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Default.PanTool,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Drag the pin anywhere on screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Login ──────────────────────────────────────────────────────────────

@Composable
private fun LoginPage() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val heroAlpha by animateFloatAsState(if (appeared) 1f else 0f, tween(380), label = "login-alpha")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .jellySource("LoginPage.kt", 1),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(heroAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BrandMark(size = 60.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Sign in to continue to Jelly",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Section("Credentials") {
                SectionCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTextField(
                            icon = Icons.Default.Email,
                            value = email,
                            onValueChange = { email = it },
                            label = "Email",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.semantics { testTag = "email-field" },
                        )
                        IconTextField(
                            icon = Icons.Default.Lock,
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            trailing = {
                                IconButton(onClick = { reveal = !reveal }) {
                                    Icon(
                                        if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (reveal) "Hide password" else "Show password",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.semantics { testTag = "password-field" },
                        )
                    }
                }
            }
        }

        item {
            PrimaryButton("Sign in", onClick = {}, modifier = Modifier.semantics { contentDescription = "Sign in" })
        }
        item {
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Forgot password?")
            }
        }
    }
}

// ─── Profile ────────────────────────────────────────────────────────────

@Composable
private fun ProfilePage() {
    var name by remember { mutableStateOf("Rajan") }
    var bio by remember { mutableStateOf("Building Jelly") }
    var notifications by remember { mutableStateOf(true) }
    val initials = remember(name) {
        name.split(' ').take(2).mapNotNull { it.firstOrNull()?.toString() }
            .joinToString("").uppercase().ifEmpty { "?" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .jellySource("ProfilePage.kt", 1),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .shadow(12.dp, CircleShape, ambientColor = BrandAccent.copy(alpha = 0.3f), spotColor = BrandAccent.copy(alpha = 0.3f))
                        .clip(CircleShape)
                        .background(brandBrush()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (name.isEmpty()) "Your name" else name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (name.isEmpty()) "@you" else "@${name.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Section("Details") {
                SectionCard {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconTextField(
                            icon = Icons.Default.Person,
                            value = name,
                            onValueChange = { name = it },
                            label = "Name",
                            modifier = Modifier.semantics { testTag = "name-field" },
                        )
                        IconTextField(
                            icon = Icons.AutoMirrored.Filled.TextSnippet,
                            value = bio,
                            onValueChange = { bio = it },
                            label = "Bio",
                            singleLine = false,
                            modifier = Modifier.semantics { testTag = "bio-field" },
                        )
                    }
                }
            }
        }

        item {
            Section("Preferences") {
                SectionCard {
                    ToggleRow("Notifications", notifications, { notifications = it })
                }
            }
        }

        item { PrimaryButton("Save changes", onClick = {}) }
        item {
            TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Delete account", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─── App Settings ───────────────────────────────────────────────────────

@Composable
private fun AppSettingsPage() {
    var darkMode by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(14) }
    var quality by remember { mutableStateOf(2) } // 0=Low, 1=Medium, 2=High

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .jellySource("AppSettingsPage.kt", 1),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Section("Appearance") {
                SectionCard {
                    ToggleRow("Dark mode", darkMode, { darkMode = it })
                    Divider()
                    StepperRow("Font size", fontSize, range = 10..24, onChange = { fontSize = it })
                }
            }
        }

        item {
            Section("Streaming") {
                SectionCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Quality", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        val labels = listOf("Low", "Medium", "High")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            labels.forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = quality == i,
                                    onClick = { quality = i },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Account") {
                SectionCard {
                    Box(modifier = Modifier.padding(16.dp)) {
                        TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign out", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ─── Catalogue ──────────────────────────────────────────────────────────

@Composable
private fun CataloguePage() {
    val items = remember { (1..30).map { "Item #$it" to (5 + (it * 17) % 195) } }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .jellySource("CataloguePage.kt", 1),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.first }) { (name, price) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = BrandAccent,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Long-press to annotate this row",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("$$price", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ─── Shared building blocks ─────────────────────────────────────────────

// Brand accent — indigo/violet. Reserved for the BrandMark and decorative
// gradients (rings, bars, step badges); interactive elements use M3 defaults.
private val BrandAccent = Color(0xFF6657EB)
private val BrandStart = Color(0xFF7C6BFB)
private val BrandEnd = Color(0xFF5B4DDB)

@Composable
private fun brandBrush() = Brush.linearGradient(
    colors = listOf(BrandStart, BrandEnd),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
)

@Composable
private fun BrandMark(size: Dp = 52.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = size * 0.18f,
                shape = RoundedCornerShape(size * 0.27f),
                ambientColor = BrandAccent.copy(alpha = 0.35f),
                spotColor = BrandAccent.copy(alpha = 0.35f),
            )
            .clip(RoundedCornerShape(size * 0.27f))
            .background(brandBrush()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun HeroHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BrandMark(size = 50.dp)
            Column {
                Text("Jelly", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "QA annotation demo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Long-press any element to capture structured feedback for your AI coding agent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

/**
 * contentPadding for a top-level (un-scaffolded) scrolling screen: the usual
 * 20×16 gutter, plus the system-bar insets so the first row clears the status
 * bar and the last clears the nav bar under edge-to-edge. Scaffolded pages
 * don't need this — their Scaffold already consumes the insets.
 */
@Composable
private fun screenPadding(): PaddingValues =
    WindowInsets.systemBars
        .add(WindowInsets(left = 20.dp, top = 16.dp, right = 20.dp, bottom = 16.dp))
        .asPaddingValues()

/**
 * A labelled group: the [SectionLabel] hugs its [content] (4dp gap), with an
 * optional [footer] below. Sections are the unit the parent list spaces apart,
 * so the label never floats away from what it labels.
 */
@Composable
private fun Section(
    label: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column {
        SectionLabel(label)
        content()
        if (footer != null) SectionFooter(footer)
    }
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun DemoRow(demo: Demo, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(demo.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(demo.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(demo.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    demo.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(brandBrush()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { testTag = "toggle-$label" },
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: $value", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (value > range.first) onChange(value - 1) },
                    enabled = value > range.first,
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                IconButton(
                    onClick = { if (value < range.last) onChange(value + 1) },
                    enabled = value < range.last,
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun IconTextField(
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier.weight(1f),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            trailingIcon = trailing,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Custom-drawn elements ──────────────────────────────────────────────
//
// None of these are native controls — they're built from Box / Canvas /
// RoundedCornerShape, with bare clickables. They prove the view-hierarchy
// probe captures elements the accessibility/semantics tree doesn't surface.

@Composable
private fun CustomChips() {
    var selected by remember { mutableStateOf("Bug") }
    val tags = listOf("Bug", "Polish", "Copy", "Layout")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        tags.forEach { tag ->
            val isSelected = selected == tag
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { selected = tag }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CustomRing(progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val r = (size.minDimension - stroke) / 2f
                val topLeft = Offset((size.width - r * 2) / 2f, (size.height - r * 2) / 2f)
                val arcSize = Size(r * 2, r * 2)
                // Track
                drawArc(
                    color = Color(0xFFE5E5E7),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                // Progress
                drawArc(
                    brush = Brush.linearGradient(listOf(BrandStart, BrandEnd)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Column {
            Text("Coverage", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "Drawn with a stroked Canvas arc",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomBars() {
    val values = listOf(0.4f, 0.72f, 0.55f, 0.92f, 0.63f, 0.8f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        values.forEach { v ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((48 * v).dp.coerceAtLeast(6.dp))
                    .clip(RoundedCornerShape(3.dp))
                    .background(brandBrush()),
            )
        }
    }
}
