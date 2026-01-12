package com.khan366kos.git

import kotlinx.serialization.Serializable

@Serializable
data class GitStatusResult(
    val branch: String,
    val staged: List<FileChange>,
    val unstaged: List<FileChange>,
    val untracked: List<String>,
    val diff: String
)

@Serializable
data class FileChange(
    val path: String,
    val status: String  // "modified", "added", "deleted", "renamed"
)