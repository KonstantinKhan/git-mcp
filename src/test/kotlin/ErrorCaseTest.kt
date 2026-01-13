package com.khan366kos.git

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ErrorCaseTest {

    @Test
    fun testErrorWhenCurrentEqualsTarget() {
        val repoPath = System.getProperty("user.dir")
        val prOps = PullRequestOperations(repoPath)

        // When current branch equals target branch, should get error
        val exception = assertFailsWith<GitException> {
            prOps.validateNotOnTargetBranch("main", "main")
        }

        assertTrue(
            exception.message?.contains("PR не открыт") == true,
            "Error message should indicate PR is not open"
        )
    }

    @Test
    fun testErrorWhenNoCommits() {
        val repoPath = System.getProperty("user.dir")
        val prOps = PullRequestOperations(repoPath)

        // Get commits from current branch to main
        val target = prOps.detectTargetBranch()
        val commits = prOps.getCommitLog(target)

        // We should have commits since we're on day-21 branch with our changes
        assertTrue(commits.isNotEmpty(), "Should have commits on day-21 branch")
    }
}