package com.ftpserver.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.ftpserver.app.utils.StorageUtils

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        private lateinit var prefs: SharedPreferences
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            prefs = requireContext().getSharedPreferences("ftp_server_prefs", Context.MODE_PRIVATE)
            
            setupPortPreference()
            setupAnonymousPreference()
            setupUsernamePreference()
            setupPasswordPreference()
            setupRootDirectoryPreference()
        }
        
        private fun setupPortPreference() {
            val portPref = findPreference<EditTextPreference>("port")
            val currentPort = prefs.getInt("port", 2121)
            portPref?.summary = currentPort.toString()
            portPref?.text = currentPort.toString()
            
            portPref?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            
            portPref?.setOnPreferenceChangeListener { _, newValue ->
                val port = (newValue as? String)?.toIntOrNull() ?: 2121
                if (port in 1024..65535) {
                    prefs.edit().putInt("port", port).apply()
                    portPref.summary = port.toString()
                    true
                } else {
                    false
                }
            }
            
            // WebDAV port
            val webDavPortPref = findPreference<EditTextPreference>("webdav_port")
            val currentWebDavPort = prefs.getInt("webdav_port", 8080)
            webDavPortPref?.summary = currentWebDavPort.toString()
            webDavPortPref?.text = currentWebDavPort.toString()
            
            webDavPortPref?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            
            webDavPortPref?.setOnPreferenceChangeListener { _, newValue ->
                val port = (newValue as? String)?.toIntOrNull() ?: 8080
                if (port in 1024..65535) {
                    prefs.edit().putInt("webdav_port", port).apply()
                    webDavPortPref.summary = port.toString()
                    true
                } else {
                    false
                }
            }
        }
        
        private fun setupAnonymousPreference() {
            val anonPref = findPreference<SwitchPreferenceCompat>("anonymous_access")
            anonPref?.isChecked = prefs.getBoolean("anonymous_access", true)
            
            anonPref?.setOnPreferenceChangeListener { _, newValue ->
                prefs.edit().putBoolean("anonymous_access", newValue as Boolean).apply()
                updateCredentialPreferencesVisibility(newValue)
                true
            }
            
            updateCredentialPreferencesVisibility(anonPref?.isChecked == true)
        }
        
        private fun setupUsernamePreference() {
            val userPref = findPreference<EditTextPreference>("username")
            val currentUsername = prefs.getString("username", "user") ?: "user"
            userPref?.summary = currentUsername
            userPref?.text = currentUsername
            
            userPref?.setOnPreferenceChangeListener { _, newValue ->
                val username = newValue as? String ?: "user"
                prefs.edit().putString("username", username).apply()
                userPref.summary = username
                true
            }
        }
        
        private fun setupPasswordPreference() {
            val passPref = findPreference<EditTextPreference>("password")
            val currentPassword = prefs.getString("password", "") ?: ""
            passPref?.summary = if (currentPassword.isNotEmpty()) "••••••••" else "Not set"
            passPref?.text = currentPassword
            
            passPref?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            
            passPref?.setOnPreferenceChangeListener { _, newValue ->
                val password = newValue as? String ?: ""
                prefs.edit().putString("password", password).apply()
                passPref.summary = if (password.isNotEmpty()) "••••••••" else "Not set"
                true
            }
        }
        
        private fun setupRootDirectoryPreference() {
            val rootPref = findPreference<Preference>("root_directory")
            val currentPath = prefs.getString("root_path", StorageUtils.getDefaultStoragePath())
            rootPref?.summary = currentPath
            
            rootPref?.setOnPreferenceClickListener {
                showDirectoryPicker()
                true
            }
        }
        
        private fun updateCredentialPreferencesVisibility(anonymousEnabled: Boolean) {
            findPreference<EditTextPreference>("username")?.isEnabled = !anonymousEnabled
            findPreference<EditTextPreference>("password")?.isEnabled = !anonymousEnabled
        }
        
        private fun showDirectoryPicker() {
            val volumes = StorageUtils.getStorageVolumes(requireContext())
            val items = volumes.map { "${it.name}\n${it.path}" }.toTypedArray()
            
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.root_directory)
                .setItems(items) { _, which ->
                    val selectedPath = volumes[which].path
                    prefs.edit().putString("root_path", selectedPath).apply()
                    findPreference<Preference>("root_directory")?.summary = selectedPath
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
