package com.gaozay.smartflight.executor

import com.gaozay.smartflight.i18n.LocalizedStringsTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BoundedProcessExecutionTest : LocalizedStringsTest() {
    @Test(timeout = 2_000) fun liveProcessWithoutEofIsTerminatedAtDeadline() {
        val process = FakeProcess(finished = false)
        val result = BoundedProcessExecution.await(process, 30)
        assertTrue(result.timedOut)
        assertTrue(process.destroyed)
        assertNull(result.exitCode)
    }

    @Test(timeout = 2_000) fun exitedParentWithOpenDescendantPipeStillHasDeadline() {
        val process = FakeProcess(finished = true)
        val result = BoundedProcessExecution.await(process, 30)
        assertTrue(result.timedOut)
        assertTrue(process.destroyed)
    }

    @Test(timeout = 2_000) fun completedProcessRetainsExitAndOutput() {
        val process = FakeProcess(finished = true, output = ByteArrayInputStream("hello".toByteArray()))
        val result = BoundedProcessExecution.await(process, 500)
        assertFalse(result.timedOut)
        assertEquals(7, result.exitCode)
        assertEquals("hello", result.output)
    }

    @Test(timeout = 2_000) fun rootSessionWithoutExitMarkerReturnsAndIsNotReusable() {
        val process = FakeProcess(finished = false)
        val result = RootSession(process).execute(ExecutorCommand("fake", "test"), timeoutMillis = 30)
        assertFalse(result.executed)
        assertTrue(process.destroyed)
        assertFalse(process.isAlive)
    }

    @Test(timeout = 2_000) fun cancellationReturnsBeforeBlockedCallAndIgnoresLateCompletion() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        var cancelled = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            cancellableBlockingCall(onCancel = { cancelled = true }) {
                entered.countDown()
                while (release.count > 0) {
                    try { release.await() } catch (_: InterruptedException) { }
                }
                finished.countDown()
                "late"
            }
            fail("Cancelled call returned a value")
        }
        try {
            assertTrue(entered.await(500, TimeUnit.MILLISECONDS))
            job.cancelAndJoin()
            assertTrue(cancelled)
            assertFalse(job.isActive)
        } finally { release.countDown() }
        assertTrue(finished.await(500, TimeUnit.MILLISECONDS))
    }

    @Test(timeout = 2_000) fun realShellOutputAndExitAreCollected() {
        val process = ProcessBuilder("sh", "-c", "printf hello; exit 7").redirectErrorStream(true).start()
        val result = BoundedProcessExecution.await(process, 500)
        assertEquals(7, result.exitCode)
        assertEquals("hello", result.output)
        assertFalse(result.timedOut)
    }

    @Test(timeout = 2_000) fun rootSessionBoundsManyShortLinesAndStillFindsExitMarker() {
        val text = "a\n".repeat(70_000) + "__SMARTFLIGHT_EXIT_0__:0\n"
        val process = FakeProcess(finished = false, output = ByteArrayInputStream(text.toByteArray()))
        val result = RootSession(process).execute(ExecutorCommand("fake", "test"), timeoutMillis = 1_000)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.length <= 64 * 1024)
    }

    @Test(timeout = 3_000) fun realExitedShellWithInheritedOutputPipeHonorsDeadline() {
        val process = ProcessBuilder("sh", "-c", "sleep 2 & exit 0").redirectErrorStream(true).start()
        val started = System.nanoTime()
        val result = BoundedProcessExecution.await(process, 50)
        // A platform may close the pipe at parent exit; either outcome must be bounded.
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_500)
        assertTrue(result.timedOut || result.exitCode == 0)
    }

    private class FakeProcess(
        private val finished: Boolean,
        private val output: InputStream = BlockingInputStream(),
    ) : Process() {
        @Volatile var destroyed = false
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = output
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 7
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (!finished) Thread.sleep(unit.toMillis(timeout))
            return finished
        }
        override fun exitValue(): Int = if (finished || destroyed) 7 else throw IllegalThreadStateException()
        override fun isAlive(): Boolean = !finished && !destroyed
        override fun destroy() {
            destroyed = true
            output.close()
        }
        override fun destroyForcibly(): Process { destroy(); return this }
    }

    private class BlockingInputStream : InputStream() {
        private val closed = CountDownLatch(1)
        override fun read(): Int {
            // Ignore interrupt like a pipe held open by another process; close releases it.
            while (closed.count > 0) {
                try { closed.await() } catch (_: InterruptedException) { }
            }
            return -1
        }
        override fun close() { closed.countDown() }
    }
}
