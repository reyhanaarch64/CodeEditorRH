package rh.code.editor.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rh.code.editor.preview.PreviewManager
import rh.code.editor.preview.PreviewSession
import rh.code.editor.syntax.SyntaxHighlighter
import java.io.File
import java.util.UUID

class EditorViewModel : ViewModel() {

    val documents = mutableStateListOf<Document>()
    private val outsideProjectDocuments = mutableStateListOf<Document>()
    val activeDocumentId = mutableStateOf<String?>(null)
    val isPreviewOpen = mutableStateOf(false)
    val previewSession = mutableStateOf<PreviewSession?>(null)
    val showNewProjectDialog = mutableStateOf(false)
    val currentProject = mutableStateOf<ProjectInfo?>(null)
    val drawerOpen = mutableStateOf(false)
    val moreDrawerOpen = mutableStateOf(false)
    val isDarkTheme = mutableStateOf(true)
    val wallpaperUri = mutableStateOf<Uri?>(null)
    val treeVersion = mutableStateOf(0) // naikkan biar tree recompose

    private val previewManager = PreviewManager()

    fun init(context: Context) {
        SyntaxHighlighter.loadRules(context)
        if (documents.isEmpty()) createNewDocument()
    }

    fun createNewDocument(name: String = "untitled.txt") {
        val doc = Document(
            id = UUID.randomUUID().toString(),
            name = name,
            content = "",
            language = SyntaxHighlighter.detectLanguage(name)
        )
        documents.add(doc)
        activeDocumentId.value = doc.id
    }

    fun openDocument(uri: Uri, name: String, content: String, path: String? = null) {
        val existing = documents.find { it.uri == uri || (path != null && it.path == path) }
        if (existing != null) {
            activeDocumentId.value = existing.id
            return
        }
        val doc = Document(
            id = UUID.randomUUID().toString(),
            name = name,
            content = content,
            uri = uri,
            path = path,
            language = SyntaxHighlighter.detectLanguage(name)
        )
        documents.add(doc)
        activeDocumentId.value = doc.id
    }

    fun updateContent(id: String, newContent: String) {
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents[index] = documents[index].copy(content = newContent, isModified = true)
        }
    }

    fun setLanguage(id: String, language: String) {
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents[index] = documents[index].copy(language = language)
        }
    }

    fun closeDocument(id: String) {
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents.removeAt(index)
            if (activeDocumentId.value == id) {
                activeDocumentId.value = documents.firstOrNull()?.id
            }
        }
        if (documents.isEmpty() && currentProject.value == null) createNewDocument()
    }

    fun setActive(id: String) { activeDocumentId.value = id }
    fun getActiveDocument(): Document? = documents.find { it.id == activeDocumentId.value }

    fun listAvailableProjects(): List<File> {
        val root = File("/sdcard/codeEditorRH")
        if (!root.exists()) return emptyList()
        return root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    fun openProject(context: Context, projectPath: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(projectPath)
                if (!dir.exists() || !dir.isDirectory) return@withContext
                outsideProjectDocuments.clear()
                outsideProjectDocuments.addAll(documents)
                val projectName = dir.name
                withContext(Dispatchers.Main) {
                    currentProject.value = ProjectInfo(projectName, projectPath)
                    documents.clear()
                    val index = File(dir, "index.html")
                    if (index.exists()) {
                        openDocument(Uri.fromFile(index), "index.html", index.readText(), index.absolutePath)
                    } else {
                        val first = dir.listFiles()?.firstOrNull { it.isFile }
                        if (first != null) {
                            openDocument(Uri.fromFile(first), first.name, first.readText(), first.absolutePath)
                        } else createNewDocument("index.html")
                    }
                    treeVersion.value++
                    drawerOpen.value = true
                }
            }
        }
    }

    fun exitProject() {
        currentProject.value = null
        documents.clear()
        documents.addAll(outsideProjectDocuments)
        if (documents.isEmpty()) createNewDocument()
        else activeDocumentId.value = documents.firstOrNull()?.id
        outsideProjectDocuments.clear()
        drawerOpen.value = false
        treeVersion.value++
    }

    fun createProject(context: Context, projectName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val root = File("/sdcard/codeEditorRH")
                if (!root.exists()) root.mkdirs()
                val projectDir = File(root, projectName)
                if (!projectDir.exists()) projectDir.mkdirs()
                val indexFile = File(projectDir, "index.html")
                if (!indexFile.exists()) {
                    indexFile.writeText("""
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>$projectName</title>
                            <style>body{font-family:system-ui;padding:20px;background:#0d0d0d;color:#e5e5e5;}</style>
                        </head>
                        <body>
                            <h1>Hello from $projectName</h1>
                            <p>Edit this file and press Run.</p>
                        </body>
                        </html>
                    """.trimIndent())
                }
                withContext(Dispatchers.Main) {
                    openProject(context, projectDir.absolutePath)
                }
            }
        }
    }

    /** parentPath kosong = root project */
    fun createInProject(parentPath: String, name: String, isFolder: Boolean) {
        val proj = currentProject.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val parent = if (parentPath.isBlank()) File(proj.rootPath) else File(parentPath)
                if (!parent.exists()) parent.mkdirs()
                if (isFolder) {
                    File(parent, name).mkdirs()
                } else {
                    val f = File(parent, name)
                    if (!f.exists()) {
                        f.writeText("")
                        withContext(Dispatchers.Main) {
                            openDocument(Uri.fromFile(f), name, "", f.absolutePath)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    treeVersion.value++ // force tree refresh
                }
            }
        }
    }

    fun listProjectTree(): List<FileNode> {
        val proj = currentProject.value ?: return emptyList()
        return buildTree(File(proj.rootPath))
    }

    private fun buildTree(dir: File): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return emptyList()
        for (f in files) {
            if (f.isDirectory) nodes.add(FileNode(f.name, f.absolutePath, true, buildTree(f)))
            else nodes.add(FileNode(f.name, f.absolutePath, false, emptyList()))
        }
        return nodes
    }

    fun saveActiveDocument(context: Context) {
        val doc = getActiveDocument() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (doc.path != null) File(doc.path).writeText(doc.content)
                    else if (doc.uri != null) {
                        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(doc.content.toByteArray()) }
                    }
                    withContext(Dispatchers.Main) {
                        val index = documents.indexOfFirst { it.id == doc.id }
                        if (index >= 0) documents[index] = documents[index].copy(isModified = false)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun openPreview(context: Context? = null) {
        val doc = getActiveDocument() ?: return
        viewModelScope.launch {
            val baseDir = doc.path?.let { File(it).parent }
            val session = previewManager.startPreview(doc.content, baseDir, context)
            if (session != null) {
                previewSession.value = session
                isPreviewOpen.value = true
            }
        }
    }

    fun closePreview() {
        viewModelScope.launch {
            previewManager.stopPreview()
            isPreviewOpen.value = false
            previewSession.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { previewManager.stopPreview() }
    }
}

data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode>
)
