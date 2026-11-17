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

    // pengaturan
    val fontSize = mutableStateOf(14f)
    val erudaEnabled = mutableStateOf(true)
    val isReadMode = mutableStateOf(false)
    val showSettings = mutableStateOf(false)
    val wordWrap = mutableStateOf(false)
    val showLineNumbers = mutableStateOf(true)
    val autoPair = mutableStateOf(true)
    val lazyHighlight = mutableStateOf(true)
    val autoRefreshPreview = mutableStateOf(false)
    val limitHugeFiles = mutableStateOf(true)

    // find / replace
    val showFindReplace = mutableStateOf(false)
    val findQuery = mutableStateOf("")
    val replaceQuery = mutableStateOf("")
    val findMatchIndex = mutableStateOf(0)
    val findMatches = mutableStateOf<List<IntRange>>(emptyList())

    // undo / redo per dokumen
    private val undoStacks = mutableMapOf<String, MutableList<String>>()
    private val redoStacks = mutableMapOf<String, MutableList<String>>()
    private val maxHistory = 50

    private val previewManager = PreviewManager()

    fun init(context: Context) {
        SyntaxHighlighter.loadRules(context)
        val prefs = context.getSharedPreferences("editor_settings", Context.MODE_PRIVATE)
        fontSize.value = prefs.getFloat("font_size", 14f)
        erudaEnabled.value = prefs.getBoolean("eruda_enabled", true)
        isDarkTheme.value = prefs.getBoolean("dark_theme", true)
        wordWrap.value = prefs.getBoolean("word_wrap", false)
        showLineNumbers.value = prefs.getBoolean("show_line_numbers", true)
        autoPair.value = prefs.getBoolean("auto_pair", true)
        lazyHighlight.value = prefs.getBoolean("lazy_highlight", true)
        autoRefreshPreview.value = prefs.getBoolean("auto_refresh", false)
        limitHugeFiles.value = prefs.getBoolean("limit_huge", true)
        if (documents.isEmpty()) createNewDocument()
    }

    fun saveSettings(context: Context) {
        context.getSharedPreferences("editor_settings", Context.MODE_PRIVATE).edit()
            .putFloat("font_size", fontSize.value)
            .putBoolean("eruda_enabled", erudaEnabled.value)
            .putBoolean("dark_theme", isDarkTheme.value)
            .putBoolean("word_wrap", wordWrap.value)
            .putBoolean("show_line_numbers", showLineNumbers.value)
            .putBoolean("auto_pair", autoPair.value)
            .putBoolean("lazy_highlight", lazyHighlight.value)
            .putBoolean("auto_refresh", autoRefreshPreview.value)
            .putBoolean("limit_huge", limitHugeFiles.value)
            .apply()
    }

    fun runFind(query: String) {
        findQuery.value = query
        val doc = getActiveDocument() ?: return
        if (query.isEmpty()) {
            findMatches.value = emptyList()
            findMatchIndex.value = 0
            return
        }
        val text = doc.content
        val matches = mutableListOf<IntRange>()
        var start = 0
        val q = query
        while (true) {
            val idx = text.indexOf(q, start, ignoreCase = true)
            if (idx < 0) break
            matches.add(idx until (idx + q.length))
            start = idx + 1
            if (matches.size > 5000) break // safety
        }
        findMatches.value = matches
        findMatchIndex.value = 0
    }

    fun findNext() {
        val m = findMatches.value
        if (m.isEmpty()) return
        findMatchIndex.value = (findMatchIndex.value + 1) % m.size
    }

    fun findPrev() {
        val m = findMatches.value
        if (m.isEmpty()) return
        findMatchIndex.value = if (findMatchIndex.value <= 0) m.lastIndex else findMatchIndex.value - 1
    }

    fun replaceCurrent() {
        val doc = getActiveDocument() ?: return
        val matches = findMatches.value
        val idx = findMatchIndex.value
        if (matches.isEmpty() || idx !in matches.indices) return
        val range = matches[idx]
        val newContent = doc.content.substring(0, range.first) +
            replaceQuery.value +
            doc.content.substring(range.last + 1)
        updateContent(doc.id, newContent)
        runFind(findQuery.value)
    }

    fun replaceAll() {
        val doc = getActiveDocument() ?: return
        val q = findQuery.value
        if (q.isEmpty()) return
        val newContent = doc.content.replace(q, replaceQuery.value, ignoreCase = true)
        updateContent(doc.id, newContent)
        runFind(q)
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
        isReadMode.value = false
        undoStacks[doc.id] = mutableListOf("")
        redoStacks[doc.id] = mutableListOf()
    }

    fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return true
        val textExts = setOf(
            "txt", "md", "markdown", "html", "htm", "xhtml", "css", "scss", "sass", "less",
            "js", "jsx", "mjs", "cjs", "ts", "tsx", "kt", "kts", "java", "py", "pyw", "php",
            "go", "sh", "bash", "zsh", "sql", "json", "xml", "svg", "yml", "yaml", "toml",
            "ini", "conf", "cfg", "properties", "gradle", "kts", "c", "cpp", "h", "hpp",
            "rs", "rb", "swift", "m", "mm", "pl", "pm", "r", "lua", "dart", "vue", "svelte",
            "gitignore", "dockerignore", "editorconfig", "env", "log", "csv", "tsv"
        )
        return textExts.contains(ext)
    }

    fun openDocument(uri: Uri, name: String, content: String, path: String? = null): Boolean {
        if (!isTextFile(name)) {
            // file binary / media, jangan buka di editor
            return false
        }
        val existing = documents.find { it.uri == uri || (path != null && it.path == path) }
        if (existing != null) {
            activeDocumentId.value = existing.id
            isReadMode.value = false
            return true
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
        isReadMode.value = false
        // init undo stack
        undoStacks[doc.id] = mutableListOf(content)
        redoStacks[doc.id] = mutableListOf()
        return true
    }

    fun updateContent(id: String, newContent: String, recordUndo: Boolean = true) {
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = documents[index].content
            if (old == newContent) return
            if (recordUndo) {
                val stack = undoStacks.getOrPut(id) { mutableListOf(old) }
                if (stack.lastOrNull() != old) stack.add(old)
                if (stack.size > maxHistory) stack.removeAt(0)
                redoStacks[id] = mutableListOf()
            }
            documents[index] = documents[index].copy(content = newContent, isModified = true)
        }
    }

    fun canUndo(id: String?): Boolean {
        if (id == null) return false
        val stack = undoStacks[id] ?: return false
        return stack.size > 1
    }

    fun canRedo(id: String?): Boolean {
        if (id == null) return false
        return (redoStacks[id]?.isNotEmpty() == true)
    }

    fun undo(id: String) {
        val stack = undoStacks[id] ?: return
        if (stack.size <= 1) return
        val current = stack.removeAt(stack.lastIndex)
        redoStacks.getOrPut(id) { mutableListOf() }.add(current)
        val prev = stack.last()
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents[index] = documents[index].copy(content = prev, isModified = true)
        }
    }

    fun redo(id: String) {
        val rstack = redoStacks[id] ?: return
        if (rstack.isEmpty()) return
        val next = rstack.removeAt(rstack.lastIndex)
        undoStacks.getOrPut(id) { mutableListOf() }.add(next)
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents[index] = documents[index].copy(content = next, isModified = true)
        }
    }

    fun toggleReadMode() {
        isReadMode.value = !isReadMode.value
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

    fun setActive(id: String) {
        activeDocumentId.value = id
        isReadMode.value = false
    }
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
        val listed = try { dir.listFiles() } catch (_: Exception) { null } ?: return emptyList()
        val files = listed
            .filter { f ->
                val n = f.name
                !n.startsWith(".") // skip hidden
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        for (f in files) {
            try {
                when {
                    f.isDirectory -> nodes.add(FileNode(f.name, f.absolutePath, true, buildTree(f)))
                    f.isFile -> nodes.add(FileNode(f.name, f.absolutePath, false, emptyList()))
                }
            } catch (_: Exception) { }
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
            val session = previewManager.startPreview(doc.content, baseDir, context, erudaEnabled.value)
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
