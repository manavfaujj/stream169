package com.example.stream.rtmp

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object Amf0 {
    const val TYPE_NUMBER: Byte = 0x00
    const val TYPE_BOOLEAN: Byte = 0x01
    const val TYPE_STRING: Byte = 0x02
    const val TYPE_OBJECT: Byte = 0x03
    const val TYPE_NULL: Byte = 0x05
    const val TYPE_ECMA_ARRAY: Byte = 0x08
    const val TYPE_OBJECT_END: Byte = 0x09

    fun writeNumber(out: DataOutputStream, value: Double) {
        out.writeByte(TYPE_NUMBER.toInt())
        out.writeDouble(value)
    }

    fun writeBoolean(out: DataOutputStream, value: Boolean) {
        out.writeByte(TYPE_BOOLEAN.toInt())
        out.writeByte(if (value) 1 else 0)
    }

    fun writeString(out: DataOutputStream, value: String) {
        out.writeByte(TYPE_STRING.toInt())
        writeUtf8(out, value)
    }

    fun writeNull(out: DataOutputStream) {
        out.writeByte(TYPE_NULL.toInt())
    }

    fun writeUtf8(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    fun writeObject(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_OBJECT.toInt())
        for ((key, value) in properties) {
            writeUtf8(out, key)
            writeValue(out, value)
        }
        // Object end marker: empty string followed by 0x09
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END.toInt())
    }

    fun writeEcmaArray(out: DataOutputStream, properties: Map<String, Any?>) {
        out.writeByte(TYPE_ECMA_ARRAY.toInt())
        out.writeInt(properties.size)
        for ((key, value) in properties) {
            writeUtf8(out, key)
            writeValue(out, value)
        }
        out.writeShort(0)
        out.writeByte(TYPE_OBJECT_END.toInt())
    }

    private fun writeValue(out: DataOutputStream, value: Any?) {
        when (value) {
            null -> writeNull(out)
            is Double -> writeNumber(out, value)
            is Number -> writeNumber(out, value.toDouble())
            is Boolean -> writeBoolean(out, value)
            is String -> writeString(out, value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(out, value as Map<String, Any?>)
            }
            else -> writeString(out, value.toString())
        }
    }
}
