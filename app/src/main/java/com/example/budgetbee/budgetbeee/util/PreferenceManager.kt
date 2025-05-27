package com.example.budgetbee.budgetbeee.util

import android.content.Context
import android.content.SharedPreferences
import com.example.budgetbee.budgetbeee.model.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import java.util.Calendar

class PreferenceManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val context = context

    companion object {
        private const val PREFS_NAME = "BudgetBeePrefs"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_MONTHLY_BUDGET = "monthly_budget"
        private const val KEY_SELECTED_CURRENCY = "selected_currency"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val DEFAULT_CURRENCY = "USD"
    }

    fun saveMonthlyBudget(budget: Double) {
        sharedPreferences.edit().putFloat(KEY_MONTHLY_BUDGET, budget.toFloat()).apply()
    }

    fun getMonthlyBudget(): Double {
        return sharedPreferences.getFloat(KEY_MONTHLY_BUDGET, 0f).toDouble()
    }

    fun getMonthlyExpenses(): Double {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return getTransactions()
            .filter {
                val transactionDate = Calendar.getInstance().apply {
                    time = it.date
                }
                transactionDate.get(Calendar.MONTH) == currentMonth &&
                        transactionDate.get(Calendar.YEAR) == currentYear &&
                        it.type == Transaction.Type.EXPENSE
            }
            .sumOf { it.amount }
    }

    fun saveCurrency(currency: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_CURRENCY, currency).apply()
    }

    fun getCurrency(): String {
        return sharedPreferences.getString(KEY_SELECTED_CURRENCY, DEFAULT_CURRENCY) ?: DEFAULT_CURRENCY
    }

    fun saveTransactions(transactions: List<Transaction>) {
        val json = gson.toJson(transactions)
        sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
    }

    fun getTransactions(): List<Transaction> {
        val json = sharedPreferences.getString(KEY_TRANSACTIONS, "[]")
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addTransaction(transaction: Transaction) {
        val currentTransactions = getTransactions().toMutableList()
        currentTransactions.add(transaction)
        saveTransactions(currentTransactions)
    }

    fun deleteTransaction(transactionId: String) {
        val currentTransactions = getTransactions().toMutableList()
        currentTransactions.removeIf { it.id == transactionId }
        saveTransactions(currentTransactions)
    }

    fun updateTransaction(updatedTransaction: Transaction) {
        val currentTransactions = getTransactions().toMutableList()
        val index = currentTransactions.indexOfFirst { it.id == updatedTransaction.id }
        if (index != -1) {
            currentTransactions[index] = updatedTransaction
            saveTransactions(currentTransactions)
        }
    }

    // Backup and Restore methods
    fun getBackupFiles(): List<File> {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir.listFiles { file ->
            file.name.startsWith("ImiliPocket_Backup_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun createBackup(backupData: String): Boolean {
        try {
            val dateFormat = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            val fileName = "ImiliPocket_Backup_${dateFormat.format(Date())}.json"
            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val backupFile = File(backupDir, fileName)
            FileWriter(backupFile).use { writer ->
                writer.write(backupData)
            }
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    fun restoreFromBackup(backupFile: File): Boolean {
        try {
            val backupData = backupFile.readText()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data = gson.fromJson<Map<String, Any>>(backupData, type)

            data["transactions"]?.let {
                val transactions = gson.fromJson<List<Transaction>>(
                    gson.toJson(it),
                    object : TypeToken<List<Transaction>>() {}.type
                )
                saveTransactions(transactions)
            }

            data["budget"]?.let {
                saveMonthlyBudget((it as Number).toDouble())
            }

            data["currency"]?.let {
                saveCurrency(it as String)
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
    }

    fun isDarkModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DARK_MODE, false)
    }
}