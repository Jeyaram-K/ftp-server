package com.ftpserver.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.ftpserver.app.databinding.ActivityMainBinding
import com.ftpserver.app.utils.NetworkUtils
import com.ftpserver.app.utils.QrCodeUtils
import com.ftpserver.app.utils.StorageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), FtpServerService.LogListener, FtpServerService.StatusListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var ftpService: FtpServerService? = null
    private var isBound = false
    private val logAdapter = LogAdapter()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FtpServerService.LocalBinder
            ftpService = binder.getService()
            ftpService?.addLogListener(this@MainActivity)
            ftpService?.addStatusListener(this@MainActivity)
            isBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            ftpService?.removeLogListener(this@MainActivity)
            ftpService?.removeStatusListener(this@MainActivity)
            ftpService = null
            isBound = false
        }
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startServer()
        } else {
            showStoragePermissionDialog()
        }
    }
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermission()) {
            startServer()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("ftp_server_prefs", Context.MODE_PRIVATE)
        
        setupUI()
        setupRecyclerView()
        updateStoragePath()
        
        // Bind to service if running
        if (FtpServerService.isRunning) {
            bindToService()
        }
        
        updateUI()
    }
    
    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (FtpServerService.isRunning) {
                stopServer()
            } else {
                checkPermissionsAndStart()
            }
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        binding.cardUrl.setOnClickListener {
            copyUrlToClipboard()
        }
        
        binding.btnSelectFolder.setOnClickListener {
            showFolderSelectionDialog()
        }
        
        binding.cardQr.setOnClickListener {
            showQrCodeDialog()
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerLog.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logAdapter
        }
    }
    
    private fun checkPermissionsAndStart() {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        // Check WiFi connection
        if (!NetworkUtils.isWifiConnected(this) && !NetworkUtils.isHotspotEnabled(this)) {
            showWifiRequiredDialog()
            return
        }
        
        // Check storage permission
        if (!hasStoragePermission()) {
            requestStoragePermission()
            return
        }
        
        startServer()
    }
    
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    
    private fun startServer() {
        val ftpPort = prefs.getInt("port", 2121)
        val webDavPort = prefs.getInt("webdav_port", 8080)
        val rootPath = prefs.getString("root_path", StorageUtils.getDefaultStoragePath()) ?: StorageUtils.getDefaultStoragePath()
        val username = prefs.getString("username", "anonymous") ?: "anonymous"
        val password = prefs.getString("password", "") ?: ""
        val allowAnonymous = prefs.getBoolean("anonymous_access", true)
        
        val intent = Intent(this, FtpServerService::class.java).apply {
            action = FtpServerService.ACTION_START
            putExtra(FtpServerService.EXTRA_PORT, ftpPort)
            putExtra(FtpServerService.EXTRA_WEBDAV_PORT, webDavPort)
            putExtra(FtpServerService.EXTRA_ROOT_PATH, rootPath)
            putExtra(FtpServerService.EXTRA_USERNAME, username)
            putExtra(FtpServerService.EXTRA_PASSWORD, password)
            putExtra(FtpServerService.EXTRA_ANONYMOUS, allowAnonymous)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        bindToService()
        
        // Update UI after a short delay
        binding.root.postDelayed({ updateUI() }, 500)
    }
    
    private fun stopServer() {
        val intent = Intent(this, FtpServerService::class.java).apply {
            action = FtpServerService.ACTION_STOP
        }
        startService(intent)
        
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        
        binding.root.postDelayed({ updateUI() }, 300)
    }
    
    private fun bindToService() {
        val intent = Intent(this, FtpServerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun updateUI() {
        val isRunning = FtpServerService.isRunning
        val ipAddress = FtpServerService.currentIpAddress
        val ftpPort = FtpServerService.currentPort
        val webDavPort = FtpServerService.currentWebDavPort
        
        // Update status text and indicator
        binding.txtStatus.text = if (isRunning) getString(R.string.status_running) else getString(R.string.status_stopped)
        
        val indicator = binding.statusIndicator.background as? GradientDrawable
        indicator?.setColor(
            ContextCompat.getColor(
                this,
                if (isRunning) R.color.status_running else R.color.status_stopped
            )
        )
        
        // Update button
        binding.btnStartStop.text = if (isRunning) getString(R.string.stop_server) else getString(R.string.start_server)
        
        // Show/hide URL and QR code cards
        binding.cardUrl.visibility = if (isRunning && ipAddress != null) View.VISIBLE else View.GONE
        binding.cardQr.visibility = if (isRunning && ipAddress != null) View.VISIBLE else View.GONE
        
        if (isRunning && ipAddress != null) {
            val ftpUrl = "ftp://$ipAddress:$ftpPort"
            val webDavUrl = "http://$ipAddress:$webDavPort"
            
            binding.txtFtpUrl.text = ftpUrl
            binding.txtWebDavUrl.text = webDavUrl
            
            // Set click listeners to copy URLs
            binding.txtFtpUrl.setOnClickListener { copyToClipboard("FTP URL", ftpUrl) }
            binding.txtWebDavUrl.setOnClickListener { copyToClipboard("WebDAV URL", webDavUrl) }
            
            // Generate QR code for WebDAV (better for opening files)
            Thread {
                val qrBitmap = QrCodeUtils.generateQrCode(webDavUrl, 400)
                binding.imgQrCode.post { binding.imgQrCode.setImageBitmap(qrBitmap) }
            }.start()
        }
        
        // Update log visibility
        binding.txtNoConnections.visibility = if (logAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }
    
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateStoragePath() {
        val path = prefs.getString("root_path", StorageUtils.getDefaultStoragePath())
        binding.txtStoragePath.text = path
    }
    
    private fun copyUrlToClipboard() {
        val url = binding.txtFtpUrl.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("FTP URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
    }
    
    private fun showFolderSelectionDialog() {
        val volumes = StorageUtils.getStorageVolumes(this)
        val items = volumes.map { "${it.name}\n${it.path}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(R.string.select_folder)
            .setItems(items) { _, which ->
                val selectedPath = volumes[which].path
                prefs.edit().putString("root_path", selectedPath).apply()
                updateStoragePath()
                
                // Restart server if running
                if (FtpServerService.isRunning) {
                    stopServer()
                    binding.root.postDelayed({ startServer() }, 500)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun showQrCodeDialog() {
        val ipAddress = FtpServerService.currentIpAddress ?: return
        val port = FtpServerService.currentPort
        val ftpUrl = "ftp://$ipAddress:$port"
        
        Thread {
            val qrBitmap = QrCodeUtils.generateQrCode(ftpUrl, 800)
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.scan_qr_code)
                    .setView(android.widget.ImageView(this@MainActivity).apply {
                        setImageBitmap(qrBitmap)
                        setPadding(48, 48, 48, 48)
                    })
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }.start()
    }
    
    private fun showWifiRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.wifi_not_connected)
            .setMessage(R.string.connect_to_wifi)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                requestStoragePermission()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    override fun onLog(message: String, type: FtpServerService.LogType) {
        runOnUiThread {
            logAdapter.addLog(LogItem(message, type, System.currentTimeMillis()))
            binding.recyclerLog.smoothScrollToPosition(logAdapter.itemCount - 1)
            binding.txtNoConnections.visibility = View.GONE
        }
    }
    
    override fun onStatusChanged(running: Boolean, ipAddress: String?, ftpPort: Int, webDavPort: Int) {
        runOnUiThread {
            updateUI()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        updateStoragePath()
    }
    
    override fun onDestroy() {
        if (isBound) {
            ftpService?.removeLogListener(this)
            ftpService?.removeStatusListener(this)
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}

data class LogItem(
    val message: String,
    val type: FtpServerService.LogType,
    val timestamp: Long
)

class LogAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<LogAdapter.ViewHolder>() {
    
    private val logs = mutableListOf<LogItem>()
    
    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
    
    class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val txtMessage: android.widget.TextView = itemView.findViewById(R.id.txtLogMessage)
        val txtTime: android.widget.TextView = itemView.findViewById(R.id.txtLogTime)
        val indicator: View = itemView.findViewById(R.id.logIndicator)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.txtMessage.text = log.message
        
        holder.txtTime.text = timeFormat.format(Date(log.timestamp))
        
        val indicatorColor = when (log.type) {
            FtpServerService.LogType.INFO -> R.color.secondary
            FtpServerService.LogType.CONNECTION -> R.color.status_running
            FtpServerService.LogType.DISCONNECTION -> R.color.status_starting
            FtpServerService.LogType.ERROR -> R.color.status_stopped
            FtpServerService.LogType.WEBDAV -> R.color.accent
        }
        
        val drawable = holder.indicator.background as? GradientDrawable
        drawable?.setColor(ContextCompat.getColor(holder.itemView.context, indicatorColor))
    }
    
    override fun getItemCount() = logs.size
    
    fun addLog(log: LogItem) {
        logs.add(0, log)
        if (logs.size > 100) {
            logs.removeAt(logs.lastIndex)
        }
        notifyItemInserted(0)
    }
}
