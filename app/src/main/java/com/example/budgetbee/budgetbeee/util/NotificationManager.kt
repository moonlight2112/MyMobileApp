package com.example.budgetbee.budgetbeee.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import com.example.budgetbee.budgetbeee.R
import com.example.budgetbee.budgetbeee.ui.MainActivity

class BudgetNotificationManager(private val context: Context) {
    companion object {
        private const val CHANNEL_ID_BUDGET = "budget_alerts"
        private const val CHANNEL_ID_REMINDER = "daily_reminders"
        private const val NOTIFICATION_ID_BUDGET = 1
        private const val NOTIFICATION_ID_REMINDER = 2
        
        private const val PREF_NAME = "notification_preferences"
        private const val PREF_BUDGET_ALERTS_ENABLED = "budget_alerts_enabled"
        private const val PREF_DAILY_REMINDERS_ENABLED = "daily_reminders_enabled"
        private const val PREF_REMINDER_TIME = "reminder_time"
    }

    init {
        createNotificationChannels()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val budgetChannel = NotificationChannel(
                CHANNEL_ID_BUDGET,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for budget thresholds"
            }

            val reminderChannel = NotificationChannel(
                CHANNEL_ID_REMINDER,
                "Daily Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminders to record expenses"
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(budgetChannel)
            notificationManager?.createNotificationChannel(reminderChannel)
        }
    }

    fun showBudgetAlert(spentAmount: Double, totalBudget: Double) {
        if (!areBudgetAlertsEnabled() || !hasNotificationPermission()) return

        val percentage = (spentAmount / totalBudget) * 100
        val message = when {
            percentage >= 100 -> "You have exceeded your monthly budget!"
            percentage >= 90 -> "You have used 90% of your monthly budget"
            percentage >= 75 -> "You have used 75% of your monthly budget"
            else -> return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budget Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BUDGET, builder.build())
        } catch (e: SecurityException) {
            // Handle the security exception (permission denied)
        }
    }

    fun showDailyReminder() {
        if (!areDailyRemindersEnabled() || !hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Expense Reminder")
            .setContentText("Don't forget to record your expenses for today!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMINDER, builder.build())
        } catch (e: SecurityException) {
            // Handle the security exception (permission denied)
        }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_BUDGET_ALERTS_ENABLED, enabled)
            .apply()
    }

    fun setDailyRemindersEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_DAILY_REMINDERS_ENABLED, enabled)
            .apply()
    }

    fun setReminderTime(hourOfDay: Int, minute: Int) {
        val timeInMinutes = hourOfDay * 60 + minute
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_REMINDER_TIME, timeInMinutes)
            .apply()
    }

    fun areBudgetAlertsEnabled(): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_BUDGET_ALERTS_ENABLED, true)
    }

    fun areDailyRemindersEnabled(): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_DAILY_REMINDERS_ENABLED, true)
    }

    fun getReminderTime(): Pair<Int, Int> {
        val timeInMinutes = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_REMINDER_TIME, 20 * 60) // Default to 8:00 PM
        return Pair(timeInMinutes / 60, timeInMinutes % 60)
    }
} 