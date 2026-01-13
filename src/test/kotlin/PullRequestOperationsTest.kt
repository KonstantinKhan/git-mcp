package com.khan366kos.git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PullRequestOperationsTest {

    private val repoPath = System.getProperty("user.dir")

    @Test
    fun testDetectTargetBranch() {
        val prOps = PullRequestOperations(repoPath)
        val target = prOps.detectTargetBranch()

        assertTrue(target == "main" || target == "master", "Target branch should be main or master")
    }

    @Test
    fun testGetCurrentBranch() {
        val prOps = PullRequestOperations(repoPath)
        val currentBranch = prOps.getCurrentBranch()

        assertNotNull(currentBranch, "Current branch should not be null")
        assertTrue(currentBranch.isNotBlank(), "Current branch should not be blank")
    }

    @Test
    fun testGetCommitLog() {
        val prOps = PullRequestOperations(repoPath)
        val target = prOps.detectTargetBranch()
        val commits = prOps.getCommitLog(target)

        // Should have at least 2 commits (our test commits)
        assertTrue(commits.size >= 2, "Should have at least 2 commits")

        // Verify commit structure
        val firstCommit = commits.first()
        assertNotNull(firstCommit.hash, "Commit hash should not be null")
        assertNotNull(firstCommit.author, "Commit author should not be null")
        assertNotNull(firstCommit.subject, "Commit subject should not be null")
        assertTrue(firstCommit.hash.length >= 7, "Hash should be at least 7 characters")
    }

    @Test
    fun testGetFileStatistics() {
        val prOps = PullRequestOperations(repoPath)
        val target = prOps.detectTargetBranch()
        val fileStats = prOps.getFileStatistics(target)

        assertTrue(fileStats.isNotEmpty(), "Should have file statistics")

        // Verify file stats structure
        val firstFile = fileStats.first()
        assertNotNull(firstFile.path, "File path should not be null")
        assertTrue(firstFile.additions >= 0, "Additions should be non-negative")
        assertTrue(firstFile.deletions >= 0, "Deletions should be non-negative")
    }

    @Test
    fun testGetDiff() {
        val prOps = PullRequestOperations(repoPath)
        val target = prOps.detectTargetBranch()
        val diff = prOps.getDiff(target, 3)

        assertTrue(diff.isNotBlank(), "Diff should not be blank")
        assertTrue(diff.contains("diff --git"), "Diff should contain git diff header")
    }

    @Test
    fun testBuildPRDescription() {
        val prOps = PullRequestOperations(repoPath)
        val commits = listOf(
            CommitInfo(
                hash = "abc123def456",
                author = "Test Author",
                email = "test@example.com",
                subject = "Test commit",
                body = "This is a test commit body"
            )
        )

        val description = prOps.buildPRDescription(commits)

        assertTrue(description.contains("Коммиты в этом PR"), "Description should contain commit section")
        assertTrue(description.contains("Test commit"), "Description should contain commit subject")
        assertTrue(description.contains("Test Author"), "Description should contain author name")
    }

    @Test
    fun testExtractUniqueAuthors() {
        val prOps = PullRequestOperations(repoPath)
        val commits = listOf(
            CommitInfo("hash1", "Author A", "a@test.com", "Commit 1", ""),
            CommitInfo("hash2", "Author B", "b@test.com", "Commit 2", ""),
            CommitInfo("hash3", "Author A", "a@test.com", "Commit 3", "")
        )

        val authors = prOps.extractUniqueAuthors(commits)

        assertEquals(2, authors.size, "Should have 2 unique authors")
        assertTrue(authors.contains("Author A"), "Should contain Author A")
        assertTrue(authors.contains("Author B"), "Should contain Author B")
    }

    @Test
    fun testValidateNotOnTargetBranch() {
        val prOps = PullRequestOperations(repoPath)

        // Should throw exception when current == target
        assertFailsWith<GitException> {
            prOps.validateNotOnTargetBranch("main", "main")
        }
    }
}