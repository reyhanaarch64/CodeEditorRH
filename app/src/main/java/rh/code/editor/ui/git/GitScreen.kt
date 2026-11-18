package rh.code.editor.ui.git

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.data.ProjectInfo
import rh.code.editor.git.GitViewModel
import rh.code.editor.ui.theme.*
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun GitScreen(
    gitViewModel: GitViewModel,
    project: ProjectInfo?,
    projects: List<File>,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onBeforeGitOperation: suspend () -> Unit
) {
    val context = LocalContext.current
    val root = project?.let { File(it.rootPath) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var authorEmail by remember { mutableStateOf("") }
    var remoteUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }
    var diffTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { gitViewModel.checkToken(context) }
    LaunchedEffect(root?.absolutePath) {
        if (root != null) {
            gitViewModel.refresh(root)
            gitViewModel.checkToken(context)
            val prefs = context.getSharedPreferences("git_settings", Context.MODE_PRIVATE)
            authorName = prefs.getString("author_name", "") ?: ""
            authorEmail = prefs.getString("author_email", "") ?: ""
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().background(Surface).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GitAction(Icons.Default.ArrowBack, "kembali", onBack)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Git & GitHub", color = TextPrimary, fontSize = 17.sp)
                Text(project?.name ?: "pilih atau clone project", color = TextSecondary, fontSize = 12.sp)
            }
            if (root != null) GitAction(Icons.Default.Refresh, "refresh") { gitViewModel.refresh(root) }
        }

        if (project == null) {
            LazyColumn(Modifier.weight(1f).padding(14.dp)) {
                item {
                    GitButton(Icons.Default.CloudDownload, "Clone dari GitHub") {
                        showCloneDialog = true
                    }
                    Spacer(Modifier.height(8.dp))
                    GitButton(Icons.Default.Settings, "Atur token GitHub") {
                        showTokenDialog = true
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Project lokal", color = TextSecondary, fontSize = 13.sp)
                }
                items(projects, key = { it.absolutePath }) { folder ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenProject(folder.absolutePath) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Primary)
                        Spacer(Modifier.width(10.dp))
                        Text(folder.name, color = TextPrimary)
                    }
                }
            }
        } else {
            val snapshot = gitViewModel.snapshot.value
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("branch: ${snapshot?.branch ?: "..."}", color = TextPrimary, modifier = Modifier.weight(1f))
                GitAction(Icons.Default.Settings, "remote/token") { showRemoteDialog = true }
                GitAction(Icons.Default.CloudDownload, "pull") {
                    gitViewModel.pull(root!!, context, onBeforeGitOperation)
                }
                GitAction(Icons.Default.CloudUpload, "push") {
                    gitViewModel.push(root!!, context, onBeforeGitOperation)
                }
            }
            Text(
                snapshot?.remoteUrl ?: "remote origin belum diatur",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GitButton(Icons.Default.Code, "stage semua") {
                    gitViewModel.stageAll(root!!, onBeforeGitOperation)
                }
                GitButton(Icons.Default.Commit, "commit") { showCommitDialog = true }
            }

            gitViewModel.error.value?.let {
                Text(it, color = Error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp))
            }
            if (gitViewModel.busy.value) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = Primary)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                val changes = snapshot?.changes.orEmpty()
                if (changes.isEmpty()) {
                    item { Text("working tree clean", color = TextSecondary, modifier = Modifier.padding(12.dp)) }
                }
                items(changes, key = { it.path }) { change ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Surface).padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                change.path,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f).clickable {
                                    diffTitle = change.path
                                    gitViewModel.loadDiff(root!!, change.path, change.staged)
                                }
                            )
                            Text(
                                when {
                                                change.conflicting -> "conflict"
                                    change.untracked -> "untracked"
                                    change.unstaged -> if (change.staged) "staged + modified" else "modified"
                                    change.staged -> "staged"
                                    else -> "changed"
                                },
                                color = if (change.unstaged || change.untracked) Primary else Success,
                                fontSize = 10.sp
                            )
                            TextButton(onClick = {
                                gitViewModel.stage(root!!, change.path, !change.staged, onBeforeGitOperation)
                            }) {
                                Text(if (change.staged) "unstage" else "stage", fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                item {
                    Text("riwayat", color = Primary, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
                items(snapshot?.commits.orEmpty(), key = { it.id }) { commit ->
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(commit.shortMessage, color = TextPrimary, fontSize = 12.sp)
                        Text(
                            "${commit.author} · ${DateFormat.getDateTimeInstance().format(Date(commit.time * 1000))}",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }

    if (showRemoteDialog && root != null) {
        RemoteDialog(
            initialUrl = gitViewModel.snapshot.value?.remoteUrl.orEmpty(),
            tokenExists = gitViewModel.tokenExists.value,
            token = token,
            onTokenChange = { token = it },
            onDismiss = { showRemoteDialog = false; token = "" },
            onSaveRemote = {
                remoteUrl = it
                gitViewModel.configureRemote(root, remoteUrl)
                showRemoteDialog = false
            },
            onSaveToken = {
                gitViewModel.saveToken(context, token)
                token = ""
            },
            onDeleteToken = { gitViewModel.deleteToken(context) }
        )
    }

    if (showCommitDialog && root != null) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            containerColor = Surface,
            title = { Text("Commit", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GitTextField(commitMessage, { commitMessage = it }, "commit message")
                    GitTextField(authorName, { authorName = it }, "nama author")
                    GitTextField(authorEmail, { authorEmail = it }, "email author", KeyboardType.Email)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("git_settings", Context.MODE_PRIVATE).edit()
                        .putString("author_name", authorName).putString("author_email", authorEmail).apply()
                    gitViewModel.commit(root, commitMessage, authorName, authorEmail, onBeforeGitOperation)
                    commitMessage = ""
                    showCommitDialog = false
                }) { Text("Commit") }
            },
            dismissButton = { TextButton(onClick = { showCommitDialog = false }) { Text("batal") } }
        )
    }

    if (showCloneDialog) {
        AlertDialog(
            onDismissRequest = { showCloneDialog = false },
            containerColor = Surface,
            title = { Text("Clone repository GitHub", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GitTextField(cloneUrl, { cloneUrl = it }, "https://github.com/user/repo.git")
                    Text(
                        if (gitViewModel.tokenExists.value) "token tersimpan terenkripsi" else "repo privat perlu token",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = cloneUrl.trim().substringAfterLast('/').removeSuffix(".git")
                    if (name.matches(Regex("[A-Za-z0-9._-]+")) && name != "." && name != "..") {
                        val destination = File("/sdcard/codeEditorRH", name).canonicalFile
                        gitViewModel.clone(cloneUrl.trim(), destination, context) {
                            showCloneDialog = false
                            onOpenProject(destination.absolutePath)
                        }
                    }
                }) { Text("clone") }
            },
            dismissButton = { TextButton(onClick = { showCloneDialog = false }) { Text("batal") } }
        )
    }

    if (showTokenDialog) {
        TokenDialog(
            token = token,
            tokenExists = gitViewModel.tokenExists.value,
            onTokenChange = { token = it },
            onSave = {
                gitViewModel.saveToken(context, token)
                token = ""
                showTokenDialog = false
            },
            onDelete = { gitViewModel.deleteToken(context) },
            onDismiss = { showTokenDialog = false; token = "" }
        )
    }

    gitViewModel.diffText.value?.let { diff ->
        AlertDialog(
            onDismissRequest = { gitViewModel.diffText.value = null },
            containerColor = Surface,
            title = { Text(diffTitle, color = TextPrimary, fontSize = 14.sp) },
            text = {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    item { Text(diff.ifBlank { "diff kosong" }, color = TextPrimary, fontSize = 11.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = { gitViewModel.diffText.value = null }) { Text("tutup") }
            }
        )
    }
}

@Composable
private fun TokenDialog(
    token: String,
    tokenExists: Boolean,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Token GitHub", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("Personal Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (tokenExists) "token tersimpan terenkripsi di penyimpanan privat aplikasi" else "token belum disimpan",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onSave, enabled = token.isNotBlank()) { Text("simpan terenkripsi") }
                if (tokenExists) TextButton(onClick = onDelete) { Text("hapus token") }
                TextButton(onClick = onDismiss) { Text("tutup") }
            }
        }
    )
}

@Composable
private fun RemoteDialog(
    initialUrl: String,
    tokenExists: Boolean,
    token: String,
    onTokenChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveRemote: (String) -> Unit,
    onSaveToken: () -> Unit,
    onDeleteToken: () -> Unit
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Remote & autentikasi", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GitTextField(url, { url = it }, "HTTPS URL GitHub")
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("GitHub Personal Access Token") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (tokenExists) "token tersimpan terenkripsi di penyimpanan privat aplikasi" else "token belum disimpan",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onSaveRemote(url) }) { Text("simpan remote") }
                TextButton(onClick = onSaveToken, enabled = token.isNotBlank()) { Text("simpan token terenkripsi") }
                if (tokenExists) TextButton(onClick = onDeleteToken) { Text("hapus token") }
                TextButton(onClick = onDismiss) { Text("tutup") }
            }
        }
    )
}

@Composable
private fun GitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GitAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Text(label, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun GitButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(SurfaceElevated).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}
