package com.google.android.diskusage.datasource.fast

import androidx.annotation.Keep
import com.google.android.diskusage.utils.AppHelper.appContext
import com.google.android.diskusage.utils.DeviceHelper.isDeviceRooted
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

/**
 * Reads the serialised directory tree produced by `libscan.so`.
 *
 * The tree is generated in-process through JNI (`app/src/main/jni/scan.c`) and
 * streamed chunk by chunk into a pipe, so the parser and the progress dialog see
 * data while the scan is still running. The historical approach — executing the
 * shared library as a child process — does not work on Android 10+ (the ELF has
 * no startup routine and the platform forbids executing app binaries); it is
 * only attempted for root mode.
 */
class NativeScannerStream private constructor(
    private val input: InputStream,
    private val process: Process?,
    private val producer: Thread?
) : InputStream() {

    @Throws(IOException::class)
    override fun read(): Int = input.read()

    @Throws(IOException::class)
    override fun read(buffer: ByteArray): Int = input.read(buffer)

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int =
        input.read(buffer, byteOffset, byteCount)

    @Throws(IOException::class)
    override fun close() {
        try {
            // Closing the read end unblocks a producer that is stuck writing to
            // a full pipe.
            input.close()
        } finally {
            process?.let { awaitExit(it) }
            producer?.let { thread ->
                thread.interrupt()
                runCatching { thread.join(CLOSE_TIMEOUT_MS) }
            }
        }
    }

    /**
     * Waits for the child process to terminate, but never blocks forever: a
     * stuck helper used to leave the app on the "Scanning directories" dialog.
     */
    private fun awaitExit(process: Process, timeoutMs: Long = EXIT_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return
            } catch (e: IllegalThreadStateException) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        process.destroy()
        process.destroyForcibly()
    }

    /** Receives the chunks of the serialised tree from native code. */
    class NativeSink internal constructor(private val out: OutputStream) {
        @Keep
        fun onData(chunk: ByteArray) {
            out.write(chunk)
        }
    }

    companion object Factory {
        private const val LIBSCAN = "libscan.so"
        private const val EXIT_TIMEOUT_MS = 5_000L
        private const val CLOSE_TIMEOUT_MS = 1_000L
        private const val POLL_INTERVAL_MS = 20L
        private const val PIPE_SIZE = 64 * 1024

        private val nativeAvailable: Boolean = try {
            System.loadLibrary("scan")
            true
        } catch (t: Throwable) {
            Timber.w(t, "libscan.so could not be loaded")
            false
        }

        @JvmStatic
        @Throws(IOException::class)
        fun create(path: String, rootRequired: Boolean): NativeScannerStream {
            if (rootRequired && isDeviceRooted()) {
                return runScannerAsRoot(path)
            }
            if (nativeAvailable) {
                try {
                    return nativeScanner(path)
                } catch (t: Throwable) {
                    Timber.w(t, "native scan of %s could not be started", path)
                }
            }
            // Fall back to the child process; the caller falls back to the Java
            // scanner if this fails as well.
            return runScanner(path)
        }

        private fun nativeScanner(path: String): NativeScannerStream {
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(PIPE_SIZE)
            pipeOut.connect(pipeIn)
            val sink = NativeSink(pipeOut)
            val producer = Thread({
                var complete = false
                try {
                    complete = scanNative(path, sink)
                } catch (t: Throwable) {
                    Timber.w(t, "native scan of %s failed", path)
                } finally {
                    if (!complete) {
                        Timber.w("native scan of %s was not completed", path)
                    }
                    runCatching { pipeOut.close() }
                }
            }, "diskusage-scan")
            producer.isDaemon = true
            producer.start()
            return NativeScannerStream(pipeIn, null, producer)
        }

        /**
         * Runs the tree walker in-process and streams the serialised tree into
         * [sink]. Returns false when the walk was aborted.
         */
        @JvmStatic
        private external fun scanNative(path: String, sink: NativeSink): Boolean

        private fun runScanner(root: String): NativeScannerStream {
            val libscanPath = "${appContext.applicationInfo.nativeLibraryDir}/$LIBSCAN"
            val process = ProcessBuilder(libscanPath, root).start()
            return NativeScannerStream(process.inputStream, process, null)
        }

        private fun runScannerAsRoot(root: String): NativeScannerStream {
            val libscanPath = "${appContext.applicationInfo.nativeLibraryDir}/$LIBSCAN"
            val su = arrayOf("su", "/system/bin/su", "/system/xbin/su").firstNotNullOfOrNull { candidate ->
                runCatching { ProcessBuilder(candidate).start() }.getOrNull()
            } ?: throw IOException("No su binary found")

            su.outputStream.use { out ->
                out.write("$libscanPath $root\n".toByteArray())
                out.write("exit\n".toByteArray())
                out.flush()
            }
            return NativeScannerStream(su.inputStream, su, null)
        }
    }
}
