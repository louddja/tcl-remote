package com.example.tclremote

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Mini encodeur Protobuf, suffisant pour le protocole Android TV Remote v2. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) = apply { tag(field, 0); writeVarint(out, value) }
    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, 2); writeVarint(out, value.size.toLong()); out.write(value)
    }
    fun string(field: Int, value: String) = bytes(field, value.toByteArray())
    fun message(field: Int, msg: ProtoWriter) = bytes(field, msg.toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = writeVarint(out, ((field shl 3) or wire).toLong())
}

/** Message décodé : numéro de champ -> valeurs (Long pour un varint, ByteArray sinon). */
class ProtoMessage(data: ByteArray) {
    private val fields = mutableMapOf<Int, MutableList<Any>>()

    init {
        val input = data.inputStream()
        while (input.available() > 0) {
            val key = readVarint(input).toInt()
            val value: Any = when (key and 7) {
                0 -> readVarint(input)
                1 -> ByteArray(8).also { readFully(input, it) }
                2 -> ByteArray(readVarint(input).toInt()).also { readFully(input, it) }
                5 -> ByteArray(4).also { readFully(input, it) }
                else -> error("Type protobuf inconnu")
            }
            fields.getOrPut(key ushr 3) { mutableListOf() }.add(value)
        }
    }

    fun has(field: Int) = fields.containsKey(field)
    fun long(field: Int) = fields[field]?.first() as? Long ?: 0L
    fun message(field: Int) = (fields[field]?.first() as? ByteArray)?.let { ProtoMessage(it) }
}

fun writeVarint(out: OutputStream, v: Long) {
    var value = v
    while (value and 0x7FL.inv() != 0L) {
        out.write(((value and 0x7F) or 0x80).toInt())
        value = value ushr 7
    }
    out.write(value.toInt())
}

fun readVarint(input: InputStream): Long {
    var result = 0L
    var shift = 0
    while (true) {
        val b = input.read()
        if (b < 0) throw EOFException("Connexion fermée par la TV")
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
    }
}

fun readFully(input: InputStream, buf: ByteArray) {
    var off = 0
    while (off < buf.size) {
        val n = input.read(buf, off, buf.size - off)
        if (n < 0) throw EOFException("Connexion fermée par la TV")
        off += n
    }
}

/** Chaque message est précédé de sa longueur (varint). */
fun OutputStream.sendDelimited(msg: ProtoWriter) {
    val body = msg.toByteArray()
    val frame = ByteArrayOutputStream()
    writeVarint(frame, body.size.toLong())
    frame.write(body)
    write(frame.toByteArray())
    flush()
}

fun InputStream.readDelimited(): ProtoMessage {
    val buf = ByteArray(readVarint(this).toInt())
    readFully(this, buf)
    return ProtoMessage(buf)
}
