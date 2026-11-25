package com.example.tclremote

import android.content.Context
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/** Appairage, à faire une seule fois : la TV affiche un code à 6 caractères. */
class AtvPairing(private val context: Context, private val host: String) {
    private lateinit var socket: SSLSocket

    /** Étape 1 : la TV affiche le code. */
    fun start() {
        socket = CertManager.connect(context, host, 6467)
        val out = socket.outputStream
        val inp = socket.inputStream

        // PairingRequest
        out.sendDelimited(outer().message(10, ProtoWriter().string(1, "atvremote").string(2, "Telecommande TCL")))
        expectOk(inp.readDelimited())

        // Options : code hexadécimal de 6 caractères, notre rôle = saisie
        val encoding = ProtoWriter().varint(1, 3).varint(2, 6)
        out.sendDelimited(outer().message(20, ProtoWriter().message(1, encoding).varint(3, 1)))
        expectOk(inp.readDelimited())

        // Configuration
        out.sendDelimited(outer().message(30, ProtoWriter().message(1, encoding).varint(2, 1)))
        expectOk(inp.readDelimited())
    }

    /** Étape 2 : on renvoie la preuve calculée à partir du code. */
    fun finish(code: String) {
        val clean = code.trim().uppercase()
        require(clean.length == 6) { "Le code doit faire 6 caractères" }

        val client = CertManager.clientPublicKey(context)
        val server = socket.session.peerCertificates[0].publicKey as RSAPublicKey
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(client.modulus))
        md.update(unsigned(client.publicExponent))
        md.update(unsigned(server.modulus))
        md.update(unsigned(server.publicExponent))
        md.update(hex(clean.substring(2)))
        val secret = md.digest()

        // Les 2 premiers caractères du code servent de vérification
        if (secret[0] != hex(clean.substring(0, 2))[0]) {
            socket.close()
            error("Code incorrect, relancez l'appairage")
        }

        socket.outputStream.sendDelimited(outer().message(40, ProtoWriter().bytes(1, secret)))
        expectOk(socket.inputStream.readDelimited())
        socket.close()
    }

    fun cancel() = runCatching { socket.close() }

    private fun outer() = ProtoWriter().varint(1, 2).varint(2, 200) // version 2, statut OK

    private fun expectOk(msg: ProtoMessage) {
        if (msg.long(2) != 200L) error("Refusé par la TV (statut ${msg.long(2)})")
    }

    private fun unsigned(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
