package rh.code.editor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import rh.code.editor.ui.components.EditorToolbar
import rh.code.editor.ui.components.TabBar
import rh.code.editor.ui.editor.CodeEditorScreen
import rh.code.editor.ui.preview.PreviewScreen
import rh.code.editor.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CodeEditorTheme {
                val viewModel: EditorViewModel = viewModel()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    viewModel.init(context)
                }

                val activeDoc = viewModel.getActiveDocument()
                val isPreview = viewModel.isPreviewOpen.value
                val session = viewModel.previewSession.value

                if (isPreview && session != null) {
                    PreviewScreen(
                        session = session,
                        onBack = { viewModel.closePreview() }
                    )
                } else {
                    EditorApp(
                        viewModel = viewModel,
                        activeDoc = activeDoc
                    )
                }
            }
        }
    }
}

@Composable
fun EditorApp(
    viewModel: EditorViewModel,
    activeDoc: rh.code.editor.data.Document?
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    val showNewProject = viewModel.showNewProjectDialog.value

    // SAF launcher
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val name = getFileName(context, uri) ?: "file.txt"
                val content = context.contentResolver.openInputStream(uri)?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                } ?: ""
                viewModel.openDocument(uri, name, content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // permission check
    fun hasManageStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestManageStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TabBar(
            documents = viewModel.documents,
            activeId = viewModel.activeDocumentId.value,
            onTabClick = { viewModel.setActive(it) },
            onTabClose = { viewModel.closeDocument(it) },
            onAddClick = { showMenu = true }
        )

        if (activeDoc != null) {
            val canPreview = activeDoc.language == "html" ||
                    activeDoc.name.endsWith(".html", true) ||
                    activeDoc.name.endsWith(".htm", true)

            EditorToolbar(
                fileName = activeDoc.name + if (activeDoc.isModified) " •" else "",
                onSave = { viewModel.saveActiveDocument(context) },
                onPreview = { viewModel.openPreview() },
                canPreview = canPreview
            )

            CodeEditorScreen(
                document = activeDoc,
                onContentChange = { viewModel.updateContent(activeDoc.id, it) }
            )
        }
    }

    // add menu
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Tambah", color = TextPrimary, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuButton("Buka File (SAF)") {
                        showMenu = false
                        openFileLauncher.launch(arrayOf("*/*"))
                    }
                    MenuButton("Buat Project Baru") {
                        showMenu = false
                        if (!hasManageStorage()) {
                            requestManageStorage()
                        } else {
                            viewModel.showNewProjectDialog.value = true
                        }
                    }
                    MenuButton("File Baru") {
                        showMenu = false
                        viewModel.createNewDocument()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // new project dialog
    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { viewModel.showNewProjectDialog.value = false },
            containerColor = Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Nama Project", color = TextPrimary)
            },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    singleLine = true,
                    placeholder = { Text("contoh: my-web", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Border,
                        cursorColor = Primary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (projectName.isNotBlank()) {
                            viewModel.createProject(context, projectName.trim())
                            projectName = ""
                            viewModel.showNewProjectDialog.value = false
                        }
                    }
                ) {
                    Text("Buat", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.showNewProjectDialog.value = false
                    projectName = ""
                }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 16.dp)
    ) {
        Text(text = text, color = TextPrimary, fontSize = 15.sp)
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index)
        }
    }
    return name
}
