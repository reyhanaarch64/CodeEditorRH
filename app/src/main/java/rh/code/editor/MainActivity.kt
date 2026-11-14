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
            val viewModel: EditorViewModel = viewModel()
            CodeEditorTheme(isDark = viewModel.isDarkTheme.value) {
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
    var showCreateItem by remember { mutableStateOf(false) }
    var pendingCreateParent by remember { mutableStateOf("") }
    var pendingCreateIsFolder by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var projectList by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
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

    
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.wallpaperUri.value = uri
            // crop sederhana: langsung pakai, user bisa ganti lagi
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
    val projectTree = remember(viewModel.currentProject.value, viewModel.treeVersion.value) { viewModel.listProjectTree() }

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
                        onSymbolInserted = { pendingSymbol = null },
                        isDark = viewModel.isDarkTheme.value
                    )
                }
                SymbolBar(onSymbolClick = { pendingSymbol = it })
                val totalLines = activeDoc.content.count { it == '\n' } + 1
                StatusBar(line = cursorLine, column = cursorCol, language = activeDoc.language, totalLines = totalLines)
            }
        }

        // dim overlay di belakang drawer (hanya visual, tidak menutup saat diklik)
        if (viewModel.drawerOpen.value || viewModel.moreDrawerOpen.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
            )
        }

        // PROJECT DRAWER (left) - di atas overlay
        AnimatedVisibility(
            visible = viewModel.drawerOpen.value && isInProject,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ProjectDrawer(
                projectName = viewModel.currentProject.value?.name ?: "",
                tree = projectTree,
                treeVersion = viewModel.treeVersion.value,
                onFileClick = { node ->
                    try {
                        val f = File(node.path)
                        viewModel.openDocument(Uri.fromFile(f), node.name, f.readText(), node.path)
                    } catch (_: Exception) {}
                },
                onCreateInPath = { parentPath, isFolder ->
                    pendingCreateParent = parentPath
                    pendingCreateIsFolder = isFolder
                    newItemName = ""
                    showCreateItem = true
                },
                onExitProject = {
                    viewModel.exitProject()
                    viewModel.drawerOpen.value = false
                },
                onClose = { viewModel.drawerOpen.value = false }
            )
        }

        // MORE DRAWER (right) - di atas overlay
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
                        MenuBtn(Icons.Default.Folder, "Buka Project") {
                            showAddMenu = false
                            val list = viewModel.listAvailableProjects()
                            when {
                                list.isEmpty() -> viewModel.showNewProjectDialog.value = true
                                list.size == 1 -> viewModel.openProject(context, list[0].absolutePath)
                                else -> {
                                    projectList = list
                                    showProjectPicker = true
                                }
                            }
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
                    Text("Mode: ${if (viewModel.isDarkTheme.value) "Gelap" else "Terang"}", color = TextSecondary, fontSize = 13.sp)
                    MenuBtn(Icons.Default.DarkMode, "Mode Gelap") {
                        viewModel.isDarkTheme.value = true
                        showThemeSettings = false
                    }
                    MenuBtn(Icons.Default.LightMode, "Mode Terang") {
                        viewModel.isDarkTheme.value = false
                        showThemeSettings = false
                    }
                    MenuBtn(Icons.Default.Wallpaper, "Ubah Wallpaper Editor") {
                        showThemeSettings = false
                        wallpaperPicker.launch("image/*")
                    }
                    if (viewModel.wallpaperUri.value != null) {
                        MenuBtn(Icons.Default.HideImage, "Hapus Wallpaper") {
                            viewModel.wallpaperUri.value = null
                            showThemeSettings = false
                        }
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

    if (showCreateItem) {
        val isFolder = pendingCreateIsFolder
        AlertDialog(
            onDismissRequest = { showCreateItem = false; newItemName = "" },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text(if (isFolder) "Nama Folder" else "Nama File", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newItemName, onValueChange = { newItemName = it },
                    singleLine = true,
                    placeholder = { Text(if (isFolder) "components" else "index.js", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Primary, unfocusedBorderColor = Border, cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newItemName.isNotBlank()) {
                        viewModel.createInProject(pendingCreateParent, newItemName.trim(), isFolder)
                        newItemName = ""
                        showCreateItem = false
                    }
                }) { Text("Buat", color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateItem = false; newItemName = "" }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    if (showProjectPicker) {
        AlertDialog(
            onDismissRequest = { showProjectPicker = false },
            containerColor = Surface, shape = RoundedCornerShape(20.dp),
            title = { Text("Pilih Project", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    projectList.forEach { proj ->
                        MenuBtn(Icons.Default.Folder, proj.name) {
                            showProjectPicker = false
                            viewModel.openProject(context, proj.absolutePath)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showProjectPicker = false }) { Text("Batal", color = TextSecondary) }
            }
        )
    }
}
