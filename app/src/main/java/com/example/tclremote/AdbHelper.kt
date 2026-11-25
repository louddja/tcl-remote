package com.example.tclremote

import android.content.Context
import android.net.Uri
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

data class TvInput(val label: String, val id: String)

/**
 * Commandes que la TV refuse par la télécommande Wi‑Fi (menu des sources, choix d'une entrée),
 * envoyées par ADB comme depuis un PC. Nécessite « Débogage USB » activé sur la TV.
 * Au premier usage, la TV demande d'autoriser le téléphone.
 */
class AdbHelper(private val context: Context, private val host: String) {
    private var dadb: Dadb? = null

    private fun keyPair(): AdbKeyPair {
        val priv = File(context.filesDir, "adbkey")
        val pub = File(context.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    @Synchronized
    fun shell(command: String): String {
        val d = dadb ?: Dadb.create(host, 5555, keyPair()).also { dadb = it }
        return try {
            d.shell(command).output
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }

    /** Ouvre le menu des sources de la TV. */
    fun openSourceMenu() {
        shell("input keyevent KEYCODE_TV_INPUT")
    }

    /** Bascule directement sur une entrée (HDMI, antenne…). */
    fun openInput(inputId: String) {
        shell("am start -a android.intent.action.VIEW -d \"content://android.media.tv/passthrough/${Uri.encode(inputId)}\"")
    }

    /** Lit les entrées de la TV : HDMI 1, HDMI 2… puis TV (antenne). */
    fun loadInputs(): List<TvInput> {
        val out = shell("dumpsys tv_input")

        // id matériel -> numéro de prise HDMI (type 9 = HDMI)
        val hdmiPorts = Regex("""TvInputHardwareInfo \{id=(\d+), type=9[^}]*?hdmi_port=(\d+)""")
            .findAll(out)
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }

        // id matériel -> identifiant de l'entrée
        val inputIds = Regex("""(?m)^\s*(\d+): (\S+/\S+)\s*$""")
            .findAll(out)
            .associate { it.groupValues[1] to it.groupValues[2] }

        val result = mutableListOf<Pair<Int, TvInput>>()
        for ((hardwareId, inputId) in inputIds) {
            val port = hdmiPorts[hardwareId]
            when {
                port != null -> result += port to TvInput("HDMI $port", inputId)
                !inputId.contains("passthrough", ignoreCase = true) -> result += 100 to TvInput("TV", inputId)
            }
        }
        return result.sortedBy { it.first }.map { it.second }.distinctBy { it.label }
    }
}
