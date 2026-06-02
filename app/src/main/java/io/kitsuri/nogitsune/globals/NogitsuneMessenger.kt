package io.kitsuri.nogitsune.globals

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.OutputStreamWriter

object NogitsuneMessenger {

    private const val TAG = "NogitsuneMessenger"
    private const val SOCK_NAME = "TWOYI_SOCK"

    fun ping() {
        runCatching {
            LocalSocket(LocalSocket.SOCKET_SEQPACKET).use { sock ->
                sock.connect(LocalSocketAddress(SOCK_NAME))
                OutputStreamWriter(sock.outputStream).use { w ->
                    w.write("PING")
                    w.flush()
                }
            }
            Log.d(TAG, "ping ok")
        }.onFailure {
            Log.w(TAG, "ping failed", it)
        }
    }
}
