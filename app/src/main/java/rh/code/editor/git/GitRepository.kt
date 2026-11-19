package rh.code.editor.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.File

data class GitFileChange(
    val path: String,
    val staged: Boolean,
    val unstaged: Boolean,
    val untracked: Boolean,
    val conflicting: Boolean
)

data class GitCommit(
    val id: String,
    val shortMessage: String,
    val author: String,
    val time: Long
)

data class GitSnapshot(
    val branch: String,
    val remoteUrl: String?,
    val changes: List<GitFileChange>,
    val commits: List<GitCommit>,
    val isRepository: Boolean
)

class GitRepository(private val root: File) {
    fun initialize(): Git {
        val gitDir = File(root, ".git")
        return if (gitDir.exists()) {
            Git.open(root)
        } else {
            Git.init().setDirectory(root).call()
        }
    }

    fun status(): GitSnapshot {
        if (!File(root, ".git").exists()) {
            return GitSnapshot(
                branch = "belum diinisialisasi",
                remoteUrl = null,
                changes = emptyList(),
                commits = emptyList(),
                isRepository = false
            )
        }

        return initialize().use { git ->
            val status = git.status().call()
            val staged = status.added + status.changed + status.removed
            val untracked = status.untracked
            val unstaged = status.modified + status.missing
            val conflicting = status.conflicting
            val changes = (staged + untracked + unstaged + conflicting).distinct().sorted().map { path ->
                GitFileChange(
                    path = path,
                    staged = path in staged,
                    unstaged = path in unstaged,
                    untracked = path in untracked,
                    conflicting = path in conflicting
                )
            }
            val remote = git.repository.config.getString("remote", "origin", "url")
            val commits = try {
                git.log().setMaxCount(50).call().map {
                    GitCommit(
                        id = it.name,
                        shortMessage = it.shortMessage,
                        author = it.authorIdent.name,
                        time = it.commitTime.toLong()
                    )
                }.toList()
            } catch (_: NoHeadException) {
                emptyList()
            }
            GitSnapshot(git.repository.branch ?: "HEAD", remote, changes, commits, isRepository = true)
        }
    }

    fun initializeRepository(): GitSnapshot {
        initialize().use { }
        return status()
    }

    fun stage(path: String, staged: Boolean) {
        initialize().use { git ->
            if (staged) git.add().addFilepattern(path).call()
            else git.reset().addPath(path).call()
        }
    }

    fun diff(path: String, staged: Boolean): String = initialize().use { git ->
        val repository = git.repository
        val output = ByteArrayOutputStream()
        DiffFormatter(output).use { formatter ->
            formatter.setRepository(repository)
            formatter.setPathFilter(PathFilter.create(path))
            if (staged) {
                val headTree = repository.resolve("HEAD^{tree}")
                val oldTree = if (headTree == null) {
                    EmptyTreeIterator()
                } else {
                    CanonicalTreeParser().apply {
                        reset(repository.newObjectReader(), headTree)
                    }
                }
                formatter.format(oldTree, DirCacheIterator(repository.readDirCache()))
            } else if (git.status().call().untracked.contains(path)) {
                formatter.format(EmptyTreeIterator(), FileTreeIterator(repository))
            } else {
                formatter.format(DirCacheIterator(repository.readDirCache()), FileTreeIterator(repository))
            }
        }
        output.toString(Charsets.UTF_8.name())
    }

    fun commit(message: String, name: String, email: String) {
        require(message.isNotBlank()) { "commit message tidak boleh kosong" }
        require(name.isNotBlank() && email.isNotBlank()) { "nama dan email Git wajib diisi" }
        initialize().use { git ->
            git.commit()
                .setMessage(message.trim())
                .setAuthor(name.trim(), email.trim())
                .setCommitter(name.trim(), email.trim())
                .call()
        }
    }

    fun setRemote(url: String) {
        require(isGitHubRepositoryUrl(url)) { "remote harus URL HTTPS repository GitHub" }
        initialize().use { git ->
            val existing = git.remoteList().call().find { it.name == "origin" }
            if (existing == null) {
                git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(url)).call()
            } else {
                git.repository.config.setString("remote", "origin", "url", url)
                git.repository.config.save()
            }
        }
    }

    fun pull(token: String?) {
        initialize().use { git ->
            val branch = git.repository.branch
            val command = git.pull().setRemote("origin").setRemoteBranchName(branch).setRebase(true)
            credentials(token)?.let(command::setCredentialsProvider)
            val result = command.call()
            check(result.isSuccessful) { "pull gagal: ${result.mergeResult?.mergeStatus ?: "remote tidak tersedia"}" }
        }
    }

    fun push(token: String?) {
        initialize().use { git ->
            val branch = git.repository.branch
            val command = git.push().setRemote("origin")
                .setRefSpecs(org.eclipse.jgit.transport.RefSpec("HEAD:refs/heads/$branch"))
            credentials(token)?.let(command::setCredentialsProvider)
            val updates = command.call().flatMap { it.remoteUpdates }
            val rejected = updates.filter {
                it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK &&
                    it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.UP_TO_DATE
            }
            check(rejected.isEmpty()) {
                "push gagal: ${rejected.joinToString { "${it.remoteName}: ${it.status}" }}"
            }
        }
    }

    private fun credentials(token: String?) =
        token?.takeIf(String::isNotBlank)?.let {
            UsernamePasswordCredentialsProvider("x-access-token", it)
        }

}

fun cloneGitHubRepository(url: String, destination: File, token: String?) {
    require(isGitHubRepositoryUrl(url)) { "clone hanya mendukung URL HTTPS repository GitHub" }
    val parent = destination.canonicalFile.parentFile
    require(destination.name.matches(Regex("[A-Za-z0-9._-]+")) && destination.name != "." && destination.name != "..") {
        "nama folder project tidak valid"
    }
    require(parent?.canonicalPath == File("/sdcard/codeEditorRH").canonicalPath) {
        "clone hanya boleh disimpan di folder project aplikasi"
    }
    require(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true) {
        "folder tujuan tidak bisa dibuat"
    }
    Git.cloneRepository()
        .setURI(url)
        .setDirectory(destination)
        .apply {
            token?.takeIf(String::isNotBlank)?.let {
                setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", it))
            }
        }
        .call()
        .use { }
}

private fun isGitHubRepositoryUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    val segments = uri.path.trim('/').removeSuffix(".git").split('/')
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("github.com", ignoreCase = true) &&
        (uri.port == -1 || uri.port == 443) &&
        uri.userInfo == null &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        segments.size == 2 &&
        segments.all { it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".." }
}.getOrDefault(false)
