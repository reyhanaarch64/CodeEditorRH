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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

class EditorViewModel : ViewModel() {

    val documents = mutableStateListOf<Document>()
    val activeDocumentId = mutableStateOf<String?>(null)
    val isPreviewOpen = mutableStateOf(false)
    val previewSession = mutableStateOf<PreviewSession?>(null)
    val showNewProjectDialog = mutableStateOf(false)
    val showPermissionDialog = mutableStateOf(false)

    private val previewManager = PreviewManager()

    fun init(context: Context) {
        SyntaxHighlighter.loadRules(context)
        if (documents.isEmpty()) {
            createNewDocument()
        }
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
            documents[index] = documents[index].copy(
                content = newContent,
                isModified = true
            )
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
        if (documents.isEmpty()) {
            createNewDocument()
        }
    }

    fun setActive(id: String) {
        activeDocumentId.value = id
    }

    fun getActiveDocument(): Document? {
        return documents.find { it.id == activeDocumentId.value }
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
                    indexFile.writeText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>$projectName</title>
                            <style>
                                body { font-family: system-ui; padding: 20px; background: #0d0d0d; color: #e5e5e5; }
                            </style>
                        </head>
                        <body>
                            <h1>Hello from $projectName</h1>
                            <p>Edit this file and press Preview.</p>
                        </body>
                        </html>
                        """.trimIndent()
                    )
                }
                withContext(Dispatchers.Main) {
                    openDocument(
                        uri = Uri.fromFile(indexFile),
                        name = "index.html",
                        content = indexFile.readText(),
                        path = indexFile.absolutePath
                    )
                }
            }
        }
    }

    fun saveActiveDocument(context: Context) {
        val doc = getActiveDocument() ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (doc.path != null) {
                        File(doc.path).writeText(doc.content)
                    } else if (doc.uri != null) {
                        context.contentResolver.openOutputStream(doc.uri)?.use {
                            it.write(doc.content.toByteArray())
                        }
                    }
                    withContext(Dispatchers.Main) {
                        val index = documents.indexOfFirst { it.id == doc.id }
                        if (index >= 0) {
                            documents[index] = documents[index].copy(isModified = false)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun openPreview() {
        val doc = getActiveDocument() ?: return
        viewModelScope.launch {
            val baseDir = doc.path?.let { File(it).parent }
            val session = previewManager.startPreview(doc.content, baseDir)
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
        viewModelScope.launch {
            previewManager.stopPreview()
        }
    }
}
