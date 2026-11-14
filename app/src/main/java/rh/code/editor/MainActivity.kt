package rh.code.editor

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import rh.code.editor.data.EditorViewModel
import rh.code.editor.data.FileNode
import rh.code.editor.ui.components.*
import rh.code.editor.ui.editor.CodeEditorScreen
import rh.code.editor.ui.preview.PreviewScreen
import rh.code.editor.ui.theme.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeEditorTheme {
                val viewModel: EditorViewModel = viewModel()
                val context = LocalContext.current
                LaunchedEffect(Unit) { viewModel.init(context) }

                val activeDoc = viewModel.getActiveDocument()
                val isPreview = viewModel.isPreviewOpen.value
                val session = viewModel.previewSession.value

                if (isPreview && session != null) {
                    PreviewScreen(session = session, onBack = { viewModel.closePreview() })
                } else {
                    EditorApp(viewModel = viewModel, activeDoc = activeDoc)
                }
            }
        }
    }
}

@Composable
fun EditorApp(viewModel: EditorViewModel, activeDoc: rh.code.editor.data.Document?) {
    val context = LocalContext.current
    var showAddMenu by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var cursorLine by remember { mutableStateOf(1) }
    var cursorCol by remember { mutableStateOf(1) }
    var pendingSymbol by remember { mutableStateOf<String?>(null) }
    var showCreateFile by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }

    val openFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val name = getFileName(context, uri) ?: "file.txt"
                val content = context.contentResolver.openInputStream(uri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: ""
                viewModel.openDocument(uri, name, content)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun hasManageStorage() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
    fun requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    val isInProject = viewModel.currentProject.value != null
    val projectTree = remember(viewModel.currentProject.value) { viewModel.listProjectTree() }

    BackHandler(enabled = viewModel.drawerOpen.value || viewModel.moreDrawerOpen.value) {
        viewModel.drawerOpen.value = false
        viewModel.moreDrawerOpen.value = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP BAR (row 1)
            val title = if (isInProject) viewModel.currentProject.value!!.name
                        else activeDoc?.name ?: "Code Editor"
            val canPreview = activeDoc != null && (
                activeDoc.language == "html" ||
                activeDoc.name.endsWith(".html", true) ||
                activeDoc.name.endsWith(".htm", true)
            )
            TopBar(
                title = title,
                canPreview = canPreview,
                onMenuClick = {
                    if (isInProject) viewModel.drawerOpen.value = !viewModel.drawerOpen.value
                    else showAddMenu = true
                },
                onSearch = { showSearch = true },
                onSave = { viewModel.saveActiveDocument(context) },
                onPreview = { viewModel.openPreview(context) },
                onMore = { viewModel.moreDrawerOpen.value = true }
            )

            // TAB BAR (row 2 - smaller)
            TabBar(
                documents = viewModel.documents,
                activeId = viewModel.activeDocumentId.value,
                onTabClick = { viewModel.setActive(it) },
                onTabClose = { viewModel.closeDocument(it) },
                onAddClick = { showAddMenu = true }
            )

            if (activeDoc != null) {
                Box(modifier = Modifier.weight(1f)) {
                    CodeEditorScreen(
                        document = activeDoc,
                        onContentChange = { viewModel.updateContent(activeDoc.id, it) },
                        onCursorChange = { l, c -> cursorLine = l; cursorCol = c },
                        insertSymbol = pendingSymbol,
                        onSymbolInserted = { pendingSymbol = null }
                    )
                }
                SymbolBar(onSymbolClick = { pendingSymbol = it })
                val totalLines = activeDoc.content.count { it == '\n' } + 1
                StatusBar(line = cursorLine, column = cursorCol, language = activeDoc.language, totalLines = totalLines)
            }
        }

        // PROJECT DRAWER (left)
        AnimatedVisibility(
            visible = viewModel.drawerOpen.value && isInProject,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ProjectDrawer(
                projectName = viewModel.currentProject.value?.name ?: "",
                tree = projectTree,
                onFileClick = { node ->
                    try {
                        val f = File(node.path)
                        viewModel.openDocument(Uri.fromFile(f), node.name, f.readText(), node.path)
                        viewModel.drawerOpen.value = false
                    } catch (_: Exception) {}
                },
                onCreateFile = { showCreateFile = true },
                onCreateFolder = { showCreateFolder = true },
                onExitProject = {
                    viewModel.exitProject()
                    viewModel.drawerOpen.value = false
                },
                onClose = { viewModel.drawerOpen.value = false }
            )
        }

        // MORE DRAWER (right)
        AnimatedVisibility(
            visible = viewModel.moreDrawerOpen.value,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            MoreDrawer(
                isInProject = isInProject,
                onSearch = { viewModel.moreDrawerOpen.value = false; showSearch = true },
                onLanguage = { viewModel.moreDrawerOpen.value = false; showLanguagePicker = true },
                onTheme = { viewModel.moreDrawerOpen.value = false; showThemeSettings = true },
                onSave = { viewModel.moreDrawerOpen.value = false; viewModel.saveActiveDocument(context) },
                onExitProject = { viewModel.moreDrawerOpen.value = false; viewModel.exitProject() },
                onClose = { viewModel.moreDrawerOpen.value = false }
            )
        }

        // dim overlay when drawer open
        if (viewModel.drawerOpen.value || viewModel.moreDrawerOpen.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f))
                    .clickable {
                        viewModel.drawerOpen.value = false
                        viewModel.moreDrawerOpen.value = false
                    }
            )
        }
    }

    // ========== DIALOGS ==========
    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            containerColor = Surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Tambah", color = TextPrimary, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuBtn(Icons.Default.FolderOpen, "Buka File (SAF)") {
                        showAddMenu = false
                        openFileLauncher.launch(arrayOf("*/*"))
                    }
                    MenuBtn(Icons.Default.CreateNewFolder, "Buat Project Baru") {
                        showAddMenu = false
                        if (!hasManageStorage()) requestManageStorage()
                        else viewModel.showNewProjectDialog.value = true
                    }
                    MenuBtn(Icons.Default.NoteAdd, "File Baru") {
                        showAddMenu = false
                        viewModel.createNewDocument()
                    }
                    if (!isInProject) {
                        MenuBtn(Icons.Default.Folder, "Buka Project dari /sdcard/codeEditorRH") {
                            showAddMenu = false
                            // simple: list first project or create
                            val root = File("/sdcard/codeEditorRH")
                            val first = root.listFiles()?.firstOrNull { it.isDirectory }
                            if (first != null) viewModel.openProject(context, first.absolutePath)
                            else viewModel.showNewProjectDialog.value = true
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddMenu = false }) { Text("Batal", color = TextSecondary) }
            }
        )
    }

    if (showLanguagePicker && activeDoc != null) {
        val langs = listOf("plaintext", "html", "css", "javascript", "kotlin", "json", "xml", "python", "java", "php")
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text("Pilih Syntax", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    langs.forEach { lang ->
                        val sel = activeDoc.language == lang
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Primary else SurfaceElevated)
                                .clickable {
                                    viewModel.setLanguage(activeDoc.id, lang)
                                    showLanguagePicker = false
                                }
                                .padding(12.dp)
                        ) {
                            Text(lang, color = if (sel) OnPrimary else TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguagePicker = false }) { Text("Batal", color = TextSecondary) }
            }
        )
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text("Cari Teks", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("ketik kata kunci...", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Primary, unfocusedBorderColor = Border, cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) { Text("Cari", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text("Batal", color = TextSecondary) }
            }
        )
    }

    if (showThemeSettings) {
        AlertDialog(
            onDismissRequest = { showThemeSettings = false },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text("Pengaturan Theme", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mode saat ini: ${viewModel.themeMode.value}", color = TextSecondary, fontSize = 13.sp)
                    MenuBtn(Icons.Default.DarkMode, "Dark") {
                        viewModel.themeMode.value = "dark"
                        showThemeSettings = false
                    }
                    MenuBtn(Icons.Default.LightMode, "Light (coming)") {
                        viewModel.themeMode.value = "light"
                        showThemeSettings = false
                    }
                    MenuBtn(Icons.Default.Wallpaper, "Ubah Wallpaper Editor") {
                        // placeholder - full crop picker perlu activity tambahan
                        showThemeSettings = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeSettings = false }) { Text("Tutup", color = TextSecondary) }
            }
        )
    }

    if (viewModel.showNewProjectDialog.value) {
        AlertDialog(
            onDismissRequest = { viewModel.showNewProjectDialog.value = false },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text("Nama Project", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = projectName, onValueChange = { projectName = it },
                    singleLine = true,
                    placeholder = { Text("contoh: my-web", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Primary, unfocusedBorderColor = Border, cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (projectName.isNotBlank()) {
                        viewModel.createProject(context, projectName.trim())
                        projectName = ""
                        viewModel.showNewProjectDialog.value = false
                    }
                }) { Text("Buat", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.showNewProjectDialog.value = false
                    projectName = ""
                }) { Text("Batal", color = TextSecondary) }
            }
        )
    }

    if (showCreateFile || showCreateFolder) {
        val isFile = showCreateFile
        AlertDialog(
            onDismissRequest = { showCreateFile = false; showCreateFolder = false; newItemName = "" },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text(if (isFile) "Nama File" else "Nama Folder", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newItemName, onValueChange = { newItemName = it },
                    singleLine = true,
                    placeholder = { Text(if (isFile) "index.js" else "components", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Primary, unfocusedBorderColor = Border, cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newItemName.isNotBlank()) {
                        if (isFile) viewModel.createFileInProject(newItemName.trim())
                        else viewModel.createFolderInProject(newItemName.trim())
                        newItemName = ""
                        showCreateFile = false
                        showCreateFolder = false
                    }
                }) { Text("Buat", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFile = false; showCreateFolder = false; newItemName = ""
                }) { Text("Batal", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun MoreDrawer(
    isInProject: Boolean,
    onSearch: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onSave: () -> Unit,
    onExitProject: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(Surface)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Menu", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 16.sp)
            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(20.dp).clickable { onClose() })
        }
        Spacer(Modifier.height(16.dp))
        DrawerItem(Icons.Default.Search, "Cari Teks", onSearch)
        DrawerItem(Icons.Default.Code, "Pilih Syntax", onLanguage)
        DrawerItem(Icons.Default.Palette, "Theme & Wallpaper", onTheme)
        DrawerItem(Icons.Default.Save, "Save", onSave)
        if (isInProject) {
            Spacer(Modifier.height(8.dp))
            DrawerItem(Icons.Default.Logout, "Keluar Proyek", onExitProject)
        }
    }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp)
    }
}

@Composable
fun MenuBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp)
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}
