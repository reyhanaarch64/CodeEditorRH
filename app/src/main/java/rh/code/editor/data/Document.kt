package rh.code.editor.data

import android.net.Uri

data class Document(
    val id: String,
    val name: String,
    val content: String,
    val uri: Uri? = null,
    val path: String? = null,
    val isModified: Boolean = false,
    val language: String = "plaintext"
)
