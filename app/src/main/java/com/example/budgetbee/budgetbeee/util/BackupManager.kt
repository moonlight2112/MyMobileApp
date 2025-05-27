package com.example.budgetbee.budgetbeee.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import com.example.budgetbee.budgetbeee.R
import com.example.budgetbee.budgetbeee.model.Transaction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupManager(private val context: Context, private val preferenceManager: PreferenceManager) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    companion object {
        private const val BACKUP_VERSION = 1
        private const val ENCRYPTION_ALGORITHM = "AES"
        private const val SALT = "BudgetBeeSalt" // In production, use a secure random salt
        private const val DEFAULT_APP_VERSION = "1.0.0" // Default version if not found
    }

    sealed class BackupResult {
        data class Success(val file: File) : BackupResult()
        data class Error(val message: String, val exception: Exception? = null) : BackupResult()
    }

    data class BackupMetadata(
        val version: Int,
        val createdAt: Long,
        val deviceModel: String,
        val appVersion: String,
        val transactionCount: Int
    )

    enum class ExportFormat {
        JSON, TEXT
    }

    private fun createBackupMetadata(transactions: List<Transaction>): BackupMetadata {
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: DEFAULT_APP_VERSION
        } catch (e: Exception) {
            DEFAULT_APP_VERSION
        }

        return BackupMetadata(
            version = BACKUP_VERSION,
            createdAt = System.currentTimeMillis(),
            deviceModel = android.os.Build.MODEL,
            appVersion = appVersion,
            transactionCount = transactions.size
        )
    }

    fun createBackup(format: ExportFormat = ExportFormat.JSON): BackupResult {
        return try {
            val transactions = preferenceManager.getTransactions()
            val backupData = mapOf(
                "metadata" to createBackupMetadata(transactions),
                "transactions" to transactions,
                "monthlyBudget" to preferenceManager.getMonthlyBudget(),
                "currency" to preferenceManager.getCurrency(),
                "notificationPreferences" to getNotificationPreferences()
            )

            val backupDir = File(context.filesDir, "backups").apply {
                if (!exists()) mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val extension = format.name.toLowerCase()
            val backupFileName = context.getString(R.string.backup_file_name_format, timestamp, extension)
            val backupFile = File(backupDir, backupFileName)

            when (format) {
                ExportFormat.JSON -> {
                    val encryptedData = encryptData(gson.toJson(backupData))
                    FileOutputStream(backupFile).use { fos ->
                        fos.write(encryptedData)
                    }
                }
                ExportFormat.TEXT -> {
                    FileWriter(backupFile).use { writer ->
                        writer.write(formatAsText(backupData))
                    }
                }
            }

            BackupResult.Success(backupFile)
        } catch (e: Exception) {
            BackupResult.Error("Failed to create backup: ${e.message}", e)
        }
    }

    private fun formatAsText(data: Map<String, Any>): String {
        val transactions = data["transactions"] as List<Transaction>
        val monthlyBudget = data["monthlyBudget"] as Double
        val currency = data["currency"] as String
        val metadata = data["metadata"] as BackupMetadata

        val sb = StringBuilder()
        sb.append("BudgetBee Backup\n")
        sb.append("================\n\n")
        sb.append("Backup Information:\n")
        sb.append("-------------------\n")
        sb.append("Created: ${Date(metadata.createdAt)}\n")
        sb.append("Device: ${metadata.deviceModel}\n")
        sb.append("App Version: ${metadata.appVersion}\n")
        sb.append("Transaction Count: ${metadata.transactionCount}\n\n")
        sb.append("Budget Information:\n")
        sb.append("-------------------\n")
        sb.append("Monthly Budget: $currency ${String.format("%.2f", monthlyBudget)}\n\n")
        sb.append("Transactions:\n")
        sb.append("-------------\n")

        transactions.sortedByDescending { it.date }.forEach { transaction ->
            sb.append("Date: ${transaction.date}\n")
            sb.append("Title: ${transaction.title}\n")
            sb.append("Amount: $currency ${String.format("%.2f", transaction.amount)}\n")
            sb.append("Type: ${transaction.type}\n")
            sb.append("Category: ${transaction.category}\n")
            sb.append("-------------------\n")
        }

        return sb.toString()
    }

    fun exportBackupToUri(uri: Uri, format: ExportFormat = ExportFormat.JSON): BackupResult {
        return try {
            val backup = createBackup(format)
            if (backup is BackupResult.Success) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    backup.file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                BackupResult.Success(backup.file)
            } else {
                backup
            }
        } catch (e: Exception) {
            BackupResult.Error("Failed to export backup: ${e.message}", e)
        }
    }

    fun restoreFromBackup(backupFile: File): BackupResult {
        return try {
            val encryptedData = backupFile.readBytes()
            val decryptedData = decryptData(encryptedData)
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(decryptedData, type)

            // Validate backup version
            val metadata = gson.fromJson(gson.toJson(data["metadata"]), BackupMetadata::class.java)
            if (metadata.version > BACKUP_VERSION) {
                return BackupResult.Error("Backup version ${metadata.version} is not supported")
            }

            // Restore transactions
            data["transactions"]?.let {
                val transactionsType = object : TypeToken<List<Transaction>>() {}.type
                val transactions: List<Transaction> = gson.fromJson(gson.toJson(it), transactionsType)
                preferenceManager.saveTransactions(transactions)
            }

            // Restore monthly budget
            data["monthlyBudget"]?.let {
                preferenceManager.saveMonthlyBudget((it as Number).toDouble())
            }

            // Restore currency
            data["currency"]?.let {
                preferenceManager.saveCurrency(it as String)
            }

            // Restore notification preferences
            data["notificationPreferences"]?.let {
                restoreNotificationPreferences(gson.fromJson(gson.toJson(it), Map::class.java) as Map<String, Any>)
            }

            BackupResult.Success(backupFile)
        } catch (e: Exception) {
            BackupResult.Error("Failed to restore backup: ${e.message}", e)
        }
    }

    fun restoreFromUri(uri: Uri): BackupResult {
        return try {
            val tempFile = File(context.cacheDir, "temp_backup.json")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val result = restoreFromBackup(tempFile)
            tempFile.delete()
            result
        } catch (e: Exception) {
            BackupResult.Error("Failed to restore from URI: ${e.message}", e)
        }
    }

    fun getBackupFiles(): List<File> {
        val backupDir = File(context.filesDir, "backups")
        return if (backupDir.exists()) {
            backupDir.listFiles { file ->
                file.name.startsWith("BudgetBee_Backup_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun getNotificationPreferences(): Map<String, Any> {
        val prefs = context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE)
        return mapOf(
            "budget_alerts_enabled" to prefs.getBoolean("budget_alerts_enabled", true),
            "daily_reminders_enabled" to prefs.getBoolean("daily_reminders_enabled", true),
            "reminder_time" to prefs.getInt("reminder_time", 20 * 60)
        )
    }

    private fun restoreNotificationPreferences(prefs: Map<String, Any>) {
        val sharedPrefs = context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE)
        with(sharedPrefs.edit()) {
            putBoolean("budget_alerts_enabled", prefs["budget_alerts_enabled"] as Boolean)
            putBoolean("daily_reminders_enabled", prefs["daily_reminders_enabled"] as Boolean)
            putInt("reminder_time", (prefs["reminder_time"] as Number).toInt())
            apply()
        }
    }

    private fun encryptData(data: String): ByteArray {
        val key = generateKey()
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(data.toByteArray())
    }

    private fun decryptData(data: ByteArray): String {
        val key = generateKey()
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key)
        return String(cipher.doFinal(data))
    }

    private fun generateKey(): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val spec = PBEKeySpec(SALT.toCharArray(), SALT.toByteArray(), 65536, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ENCRYPTION_ALGORITHM)
    }
} 