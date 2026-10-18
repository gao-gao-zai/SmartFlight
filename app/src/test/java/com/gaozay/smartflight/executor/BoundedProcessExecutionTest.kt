package com.gaozay.smartflight.executor

import com.gaozay.smartflight.i18n.LocalizedStringsTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
