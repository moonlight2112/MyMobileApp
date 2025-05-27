package com.example.budgetbee.budgetbeee.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.budgetbee.budgetbeee.R
import com.example.budgetbee.budgetbeee.databinding.FragmentSettingsBinding
import com.example.budgetbee.budgetbeee.util.BackupManager
import com.example.budgetbee.budgetbeee.util.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var backupManager: BackupManager
    private lateinit var preferenceManager: PreferenceManager
    private var selectedFormat: BackupManager.ExportFormat = BackupManager.ExportFormat.JSON

    companion object {
        private const val TAG = "SettingsFragment"
        private const val PERMISSION_REQUEST_CODE = 123
    }

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "Starting backup export to: $uri")
                showProgress(true)
                exportBackup(uri, selectedFormat)
            }
        }
    }

    private val restoreBackupLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "Starting backup restore from: $uri")
                showProgress(true)
                restoreBackup(uri)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Storage permission granted")
            proceedWithBackup()
        } else {
            Log.d(TAG, "Storage permission denied")
            Toast.makeText(requireContext(), "Storage permission is required for backup", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        // Make sure we have a valid context
        if (!isAdded || context == null) {
            Log.e(TAG, "Fragment not properly attached")
            return
        }
        
        preferenceManager = PreferenceManager(requireContext())
        backupManager = BackupManager(requireContext(), preferenceManager)
        Log.d(TAG, "Managers initialized")

        setupCurrencySpinner()
        setupBackupButtons()
        setupDarkModeSwitch()
        Log.d(TAG, "UI setup completed")
        
        // Ensure backup directory exists
        createBackupDirectory()
    }

    private fun createBackupDirectory() {
        val backupDir = File(requireContext().filesDir, "backups")
        if (!backupDir.exists()) {
            val created = backupDir.mkdirs()
            Log.d(TAG, "Backup directory created: $created")
        }
    }

    private fun setupCurrencySpinner() {
        val currencies = resources.getStringArray(R.array.currencies)
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item, currencies)
        (binding.spinnerCurrency as? AutoCompleteTextView)?.setAdapter(adapter)

        val currentCurrency = preferenceManager.getCurrency()
        binding.spinnerCurrency.setText(currentCurrency, false)

        binding.btnSaveCurrency.setOnClickListener {
            val selectedCurrency = binding.spinnerCurrency.text.toString()
            if (selectedCurrency.isNotEmpty()) {
                preferenceManager.saveCurrency(selectedCurrency)
                Toast.makeText(requireContext(), getString(R.string.currency_updated), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBackupButtons() {
        try {
            Log.d(TAG, "Setting up backup buttons")

            binding.btnBackup.setOnClickListener {
                Log.d(TAG, "Backup button clicked")
                showBackupFormatDialog()
            }

            binding.btnRestore.setOnClickListener {
                Log.d(TAG, "Restore button clicked")
                showRestoreDialog()
            }

            Log.d(TAG, "Backup buttons setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupBackupButtons", e)
            Toast.makeText(requireContext(), "Error setting up backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showBackupFormatDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_data))
            .setItems(arrayOf("JSON", "Text")) { _, which ->
                selectedFormat = when (which) {
                    0 -> BackupManager.ExportFormat.JSON
                    1 -> BackupManager.ExportFormat.TEXT
                    else -> BackupManager.ExportFormat.JSON
                }
                checkPermissionAndProceed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupDarkModeSwitch() {
        binding.switchDarkMode.isChecked = preferenceManager.isDarkModeEnabled()
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setDarkModeEnabled(isChecked)
            activity?.recreate()
        }
    }

    private fun showProgress(show: Boolean) {
        binding.apply {
            btnBackup.isEnabled = !show
            btnRestore.isEnabled = !show
            btnSaveCurrency.isEnabled = !show
            spinnerCurrency.isEnabled = !show
            switchDarkMode.isEnabled = !show
            
            if (show) {
                progressBar.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkPermissionAndProceed() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // No permission needed for Android 10+
                showBackupLocationDialog(selectedFormat)
            } else {
                when {
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        showBackupLocationDialog(selectedFormat)
                    }
                    shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                        showPermissionRationaleDialog()
                    }
                    else -> {
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in permission check", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun proceedWithBackup() {
        Log.d(TAG, "Proceeding with backup")
        showBackupLocationDialog(selectedFormat)
    }

    private fun showBackupLocationDialog(format: BackupManager.ExportFormat) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_data))
            .setMessage(getString(R.string.choose_backup_location))
            .setPositiveButton(getString(R.string.external_storage)) { _, _ ->
                Log.d(TAG, "User chose external storage backup")
                createExternalBackup(format)
            }
            .setNegativeButton(getString(R.string.internal_storage)) { _, _ ->
                Log.d(TAG, "User chose internal storage backup")
                showProgress(true)
                createInternalBackup(format)
            }
            .show()
    }

    private fun createExternalBackup(format: BackupManager.ExportFormat) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = when (format) {
                BackupManager.ExportFormat.JSON -> "application/json"
                BackupManager.ExportFormat.TEXT -> "text/plain"
            }
            putExtra(Intent.EXTRA_TITLE, "BudgetBee_Backup_${System.currentTimeMillis()}.${format.name.toLowerCase()}")
        }
        createBackupLauncher.launch(intent)
    }

    private fun createInternalBackup(format: BackupManager.ExportFormat) {
        Log.d(TAG, "Creating internal backup with format: $format")
        try {
            when (val result = backupManager.createBackup(format)) {
                is BackupManager.BackupResult.Success -> {
                    Log.d(TAG, "Internal backup created successfully: ${result.file.absolutePath}")
                    showProgress(false)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.backup_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is BackupManager.BackupResult.Error -> {
                    Log.e(TAG, "Internal backup failed: ${result.message}", result.exception)
                    showProgress(false)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.backup_failed))
                        .setMessage(result.message)
                        .setPositiveButton(getString(R.string.ok), null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating internal backup", e)
            showProgress(false)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.backup_failed))
                .setMessage("Error: ${e.message}")
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
    }

    private fun exportBackup(uri: Uri, format: BackupManager.ExportFormat) {
        Log.d(TAG, "Exporting backup to URI: $uri")
        when (val result = backupManager.exportBackupToUri(uri, format)) {
            is BackupManager.BackupResult.Success -> {
                Log.d(TAG, "Backup exported successfully")
                showProgress(false)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is BackupManager.BackupResult.Error -> {
                Log.e(TAG, "Backup export failed: ${result.message}", result.exception)
                showProgress(false)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.export_failed))
                    .setMessage(result.message)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
            }
        }
    }

    private fun showRestoreDialog() {
        Log.d(TAG, "Showing restore dialog")
        try {
            if (!isAdded) {
                Log.e(TAG, "Fragment not attached to activity")
                return
            }

            val context = requireContext()
            val backupFiles = backupManager.getBackupFiles()
            Log.d(TAG, "Found ${backupFiles.size} backup files")

            if (backupFiles.isEmpty()) {
                Log.d(TAG, "No backup files found, showing empty state dialog")
                MaterialAlertDialogBuilder(context)
                    .setTitle("No Backups Found")
                    .setMessage("No backup files were found in internal storage. Would you like to restore from external storage?")
                    .setPositiveButton("Choose File") { _, _ ->
                        chooseBackupFile()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }

            val items = backupFiles.map { file ->
                file.name.removePrefix("BudgetBee_Backup_").removeSuffix(".json")
            }.toTypedArray()

            Log.d(TAG, "Showing backup selection dialog with ${items.size} items")
            MaterialAlertDialogBuilder(context)
                .setTitle("Choose Backup")
                .setItems(items) { _, index ->
                    Log.d(TAG, "Selected backup file: ${backupFiles[index].name}")
                    showProgress(true)
                    restoreFromFile(backupFiles[index])
                }
                .setPositiveButton("From External") { _, _ ->
                    chooseBackupFile()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing restore dialog", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun chooseBackupFile() {
        Log.d(TAG, "Opening file chooser for backup")
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            restoreBackupLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error choosing backup file", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFromFile(file: File) {
        Log.d(TAG, "Restoring from file: ${file.absolutePath}")
        try {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_restore)
                .setMessage(R.string.restore_warning)
                .setPositiveButton(R.string.restore) { _, _ ->
                    when (val result = backupManager.restoreFromBackup(file)) {
                        is BackupManager.BackupResult.Success -> {
                            Log.d(TAG, "Restore successful")
                            showProgress(false)
                            Toast.makeText(
                                requireContext(),
                                R.string.restore_success,
                                Toast.LENGTH_SHORT
                            ).show()
                            activity?.recreate()
                        }
                        is BackupManager.BackupResult.Error -> {
                            Log.e(TAG, "Restore failed: ${result.message}", result.exception)
                            showProgress(false)
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.restore_failed)
                                .setMessage(result.message)
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    showProgress(false)
                }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from file", e)
            showProgress(false)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreBackup(uri: Uri) {
        Log.d(TAG, "Restoring from URI: $uri")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_restore))
            .setMessage(getString(R.string.restore_warning))
            .setPositiveButton(getString(R.string.restore)) { _, _ ->
                when (val result = backupManager.restoreFromUri(uri)) {
                    is BackupManager.BackupResult.Success -> {
                        Log.d(TAG, "Restore from URI successful")
                        showProgress(false)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.restore_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity?.recreate()
                    }
                    is BackupManager.BackupResult.Error -> {
                        Log.e(TAG, "Restore from URI failed: ${result.message}", result.exception)
                        showProgress(false)
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.restore_failed))
                            .setMessage(result.message)
                            .setPositiveButton(getString(R.string.ok), null)
                            .show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                showProgress(false)
            }
            .show()
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Required")
            .setMessage("Storage permission is needed to create and restore backups")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
