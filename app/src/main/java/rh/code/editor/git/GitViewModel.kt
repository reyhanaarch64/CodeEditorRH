package rh.code.editor.git

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitViewModel : ViewModel() {
    val snapshot = mutableStateOf<GitSnapshot?>(null)
    val busy = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val diffText = mutableStateOf<String?>(null)
    val tokenExists = mutableStateOf(false)

    fun refresh(root: File) {
        runGit(root) { it.status() }
    }

    fun saveToken(context: Context, token: String) {
        perform {
            withContext(Dispatchers.IO) { GitHubTokenStore(context).save(token) }
            tokenExists.value = true
        }
    }

    fun deleteToken(context: Context) {
        perform {
            withContext(Dispatchers.IO) { GitHubTokenStore(context).delete() }
            tokenExists.value = false
        }
    }

    fun checkToken(context: Context) {
        perform {
            tokenExists.value = withContext(Dispatchers.IO) { GitHubTokenStore(context).read() != null }
        }
    }

    fun stage(root: File, path: String, staged: Boolean, before: suspend () -> Unit) {
        perform {
            before()
            snapshot.value = withContext(Dispatchers.IO) {
                GitRepository(root).apply { stage(path, staged) }.status()
            }
        }
    }

    fun stageAll(root: File, before: suspend () -> Unit) {
        perform {
            before()
            val newSnapshot = withContext(Dispatchers.IO) {
                val repository = GitRepository(root)
                repository.status().changes
                    .filter { !it.staged || it.unstaged || it.untracked }
                    .forEach { repository.stage(it.path, true) }
                repository.status()
            }
            snapshot.value = newSnapshot
        }
    }

    fun commit(root: File, message: String, name: String, email: String, before: suspend () -> Unit) {
        perform {
            before()
            snapshot.value = withContext(Dispatchers.IO) {
                GitRepository(root).commit(message, name, email)
                GitRepository(root).status()
            }
        }
    }

    fun configureRemote(root: File, url: String) {
        perform {
            snapshot.value = withContext(Dispatchers.IO) {
                GitRepository(root).setRemote(url)
                GitRepository(root).status()
            }
        }
    }

    fun pull(root: File, context: Context, before: suspend () -> Unit) {
        perform {
            before()
            val token = withContext(Dispatchers.IO) { GitHubTokenStore(context).read() }
            snapshot.value = withContext(Dispatchers.IO) {
                GitRepository(root).pull(token)
                GitRepository(root).status()
            }
        }
    }

    fun push(root: File, context: Context, before: suspend () -> Unit) {
        perform {
            before()
            val token = withContext(Dispatchers.IO) { GitHubTokenStore(context).read() }
            snapshot.value = withContext(Dispatchers.IO) {
                GitRepository(root).push(token)
                GitRepository(root).status()
            }
        }
    }

    fun loadDiff(root: File, path: String, staged: Boolean) {
        perform {
            diffText.value = withContext(Dispatchers.IO) {
                GitRepository(root).diff(path, staged)
            }
        }
    }

    fun clone(url: String, destination: File, context: Context, onCloned: () -> Unit) {
        perform {
            val token = withContext(Dispatchers.IO) { GitHubTokenStore(context).read() }
            withContext(Dispatchers.IO) { cloneGitHubRepository(url, destination, token) }
            onCloned()
        }
    }

    private fun runGit(root: File, operation: suspend (GitRepository) -> GitSnapshot) {
        perform {
            snapshot.value = withContext(Dispatchers.IO) { operation(GitRepository(root)) }
        }
    }

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "operasi Git gagal"
            } finally {
                busy.value = false
            }
        }
    }
}
