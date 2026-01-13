package com.khan366kos.git

import kotlinx.serialization.Serializable

/**
 * Represents complete Pull Request information including metadata, commits, file changes, and diff.
 */
@Serializable
data class PullRequestInfo(
    val title: String,                      // PR title (branch name)
    val description: String,                // Formatted commit messages
    val sourceBranch: String,               // Current branch (source)
    val targetBranch: String,               // Target branch (main/master)
    val author: String,                     // Author of the last commit
    val allAuthors: List<String>,           // All unique authors in the PR
    val commits: List<CommitInfo>,          // All commits in the PR
    val changedFiles: List<FileStats>,      // Files with statistics
    val diff: String                        // Full unified diff
)

/**
 * Represents a single commit in the Pull Request.
 */
@Serializable
data class CommitInfo(
    val hash: String,                       // Commit SHA hash
    val author: String,                     // Commit author name
    val email: String,                      // Commit author email
    val subject: String,                    // Commit subject line
    val body: String                        // Commit body (detailed message)
)

/**
 * Represents file change statistics for a single file.
 */
@Serializable
data class FileStats(
    val path: String,                       // File path
    val additions: Int,                     // Number of lines added
    val deletions: Int                      // Number of lines deleted
)