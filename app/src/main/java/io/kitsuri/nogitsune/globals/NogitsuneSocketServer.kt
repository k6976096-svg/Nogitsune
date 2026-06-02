package io.kitsuri.nogitsune.globals

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object NogitsuneSocketServer {

    private const val TAG = "NogitsuneSocket"
    private const val SOCK_NAME = "TWOYI_SOCK"
    private const val BOOT_COMPLETED = "BOOT_COMPLETED"

    private val started = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        executor.execute { listenLoop() }
    }

    private fun listenLoop() {
        var socket: LocalSocket? = null
        try {
            socket = LocalSocket(LocalSocket.SOCKET_SEQPACKET)
            socket.bind(LocalSocketAddress(SOCK_NAME, LocalSocketAddress.Namespace.ABSTRACT))
            val server = LocalServerSocket(socket.fileDescriptor)
            while (!Thread.currentThread().isInterrupted) {
                val client = server.accept()
                executor.execute { handleClient(client) }
            }
        } catch (e: IOException) {
            Log.e(TAG, "socket server failed", e)
            started.set(false)
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun handleClient(client: LocalSocket) {
        client.use { sock ->
            val input = sock.inputStream
            val buf = ByteArray(1024)
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf) ?: break
                if (n <= 0) break
                val msg = String(buf, 0, n, StandardCharsets.US_ASCII)
                if (msg.startsWith(BOOT_COMPLETED)) {
                    Log.i(TAG, "guest boot completed")
                    BootStatus.markBooted()
                }
            }
        }
    }
}
