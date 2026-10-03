package com.pranav.study.cet_study_sprint

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Ciphertext lives outside Android backups. The AES key never leaves AndroidKeyStore. */
internal interface ChatKeyStorage { fun read(): String?; fun save(key: String); fun remove() }
internal class ChatKeyVault(context: Context) : ChatKeyStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "personal_chat_key.v1"))
    private val alias = "study_sprint_personal_chat_v1"
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun read(): String? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size in 29..1024 && bytes[0].toInt() == 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        return String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8)
            .also { require(StudyChatClient.validKey(it)) }
    }
    override fun save(key: String) {
        require(StudyChatClient.validKey(key))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        require(cipher.iv.size == 12)
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(encrypted); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }
    override fun remove() {
        file.delete()
        check(!file.baseFile.exists())
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
}
