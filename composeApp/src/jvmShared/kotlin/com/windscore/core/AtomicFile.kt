package com.windscore.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun File.atomicWriteText(text: String) = atomicWriteBytes(text.toByteArray(Charsets.UTF_8))

internal fun File.atomicWriteBytes(bytes: ByteArray) {
    parentFile?.mkdirs()
    val temp = File(parentFile ?: File("."), ".$name.${System.nanoTime()}.tmp")
    try {
        FileOutputStream(temp).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        runCatching {
            Files.move(
                temp.toPath(),
                toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temp.delete()
    }
}
