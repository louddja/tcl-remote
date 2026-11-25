package com.example.tclremote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.net.ssl.SSLSocket

/** Connexion de commande : envoie les touches, répond aux pings de la TV. */
class AtvRemote(
    private val context: Context,
    private val host: String,
    private val scope: CoroutineScope,
    private val onState: (connected: Boolean, message: String) -> Unit,
) {
    @Volatile private var socket: SSLSocket? = null

    fun connect() = scope.launch(Dispatchers.IO) {
        try {
            val s = CertManager.connect(context, host, 6466)
            socket = s
            onState(true, "Connecté")
            val inp = s.inputStream
            while (true) {
                val msg = inp.readDelimited()
                when {
                    msg.has(1) -> send(configure())                                   // RemoteConfigure
                    msg.has(2) -> send(ProtoWriter().message(2, ProtoWriter().varint(1, 622))) // SetActive
                    msg.has(8) -> {                                                   // Ping -> Pong
                        val v = msg.message(8)?.long(1) ?: 0L
                        send(ProtoWriter().message(9, ProtoWriter().varint(1, v)))
                    }
                }
            }
        } catch (e: Exception) {
            socket = null
            onState(false, "Connexion perdue : ${e.message ?: "erreur"}")
        }
    }

    /** keyCode = code Android (KeyEvent.KEYCODE_...). */
    fun press(keyCode: Int) = scope.launch(Dispatchers.IO) {
        runCatching {
            send(ProtoWriter().message(10, ProtoWriter().varint(1, keyCode.toLong()).varint(2, 3))) // 3 = appui court
        }
    }

    /** Ouvre une appli via un lien (ex. "https://www.netflix.com/title" ou "market://launch?id=paquet"). */
    fun launchApp(link: String) = scope.launch(Dispatchers.IO) {
        runCatching {
            send(ProtoWriter().message(90, ProtoWriter().string(1, link))) // RemoteAppLinkLaunchRequest
        }
    }

    fun close() = runCatching { socket?.close() }

    @Synchronized
    private fun send(msg: ProtoWriter) {
        socket?.outputStream?.sendDelimited(msg)
    }

    private fun configure(): ProtoWriter {
        val info = ProtoWriter()
            .string(1, "Android")
            .string(2, "TclRemote")
            .varint(3, 1)
            .string(4, "1")
            .string(5, "com.example.tclremote")
            .string(6, "1.0.0")
        return ProtoWriter().message(1, ProtoWriter().varint(1, 622).message(2, info))
    }
}
