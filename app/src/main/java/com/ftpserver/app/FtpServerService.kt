package com.ftpserver.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ftpserver.app.ftp.AndroidFileSystemFactory
import com.ftpserver.app.utils.NetworkUtils
import com.ftpserver.app.webdav.WebDavServer
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FtpServerService : Service() {
    
    companion object {
        const val CHANNEL_ID = "ftp_server_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.ftpserver.app.START"
        const val ACTION_STOP = "com.ftpserver.app.STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_WEBDAV_PORT = "webdav_port"
        const val EXTRA_ROOT_PATH = "root_path"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_ANONYMOUS = "anonymous"
        
        var isRunning = false
            private set
        
        var currentIpAddress: String? = null
            private set
        
        var currentPort: Int = 2121
            private set
        
        var currentWebDavPort: Int = 8080
            private set
    }
    
    private var ftpServer: FtpServer? = null
    private var webDavServer: WebDavServer? = null
    private val binder = LocalBinder()
    private val logListeners = mutableListOf<LogListener>()
    private val statusListeners = mutableListOf<StatusListener>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    inner class LocalBinder : Binder() {
        fun getService(): FtpServerService = this@FtpServerService
    }
    
    interface LogListener {
        fun onLog(message: String, type: LogType)
    }
    
    interface StatusListener {
        fun onStatusChanged(running: Boolean, ipAddress: String?, ftpPort: Int, webDavPort: Int)
    }
    
    enum class LogType {
        INFO, CONNECTION, DISCONNECTION, ERROR, WEBDAV
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ftpPort = intent.getIntExtra(EXTRA_PORT, 2121)
                val webDavPort = intent.getIntExtra(EXTRA_WEBDAV_PORT, 8080)
                val rootPath = intent.getStringExtra(EXTRA_ROOT_PATH) ?: "/storage/emulated/0"
                val username = intent.getStringExtra(EXTRA_USERNAME) ?: "anonymous"
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
                val allowAnonymous = intent.getBooleanExtra(EXTRA_ANONYMOUS, true)
                
                startServers(ftpPort, webDavPort, rootPath, username, password, allowAnonymous)
            }
            ACTION_STOP -> {
                stopServers()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    private fun startServers(
        ftpPort: Int,
        webDavPort: Int,
        rootPath: String,
        username: String,
        password: String,
        allowAnonymous: Boolean
    ) {
        try {
            val ipAddress = NetworkUtils.getWifiIpAddress(this)
            if (ipAddress == null) {
                log("Failed to get IP address. Is WiFi connected?", LogType.ERROR)
                stopSelf()
                return
            }
            
            // Start FTP Server
            startFtpServer(ftpPort, rootPath, username, password, allowAnonymous)
            
            // Start WebDAV Server
            startWebDavServer(webDavPort, rootPath)
            
            isRunning = true
            currentIpAddress = ipAddress
            currentPort = ftpPort
            currentWebDavPort = webDavPort
            
            // Start foreground with notification
            val notification = createNotification(ipAddress, ftpPort, webDavPort)
            startForeground(NOTIFICATION_ID, notification)
            
            log("FTP Server started at ftp://$ipAddress:$ftpPort", LogType.INFO)
            log("WebDAV Server started at http://$ipAddress:$webDavPort", LogType.INFO)
            notifyStatusChanged()
            
        } catch (e: Exception) {
            log("Failed to start servers: ${e.message}", LogType.ERROR)
            e.printStackTrace()
            stopSelf()
        }
    }
    
    private fun startFtpServer(
        port: Int,
        rootPath: String,
        username: String,
        password: String,
        allowAnonymous: Boolean
    ) {
        val serverFactory = FtpServerFactory()
        
        // Configure listener
        val listenerFactory = ListenerFactory()
        listenerFactory.port = port
        listenerFactory.serverAddress = "0.0.0.0"
        serverFactory.addListener("default", listenerFactory.createListener())
        
        // Configure file system
        serverFactory.fileSystem = AndroidFileSystemFactory(rootPath)
        
        // Configure user manager
        val userManager = serverFactory.userManager
        
        // Create user (anonymous or with credentials)
        val user = BaseUser().apply {
            name = if (allowAnonymous) "anonymous" else username
            this.password = if (allowAnonymous) "" else password
            homeDirectory = rootPath
            authorities = listOf(WritePermission())
            maxIdleTime = 300
        }
        userManager.save(user)
        
        // Create and start server
        ftpServer = serverFactory.createServer()
        ftpServer?.start()
    }
    
    private fun startWebDavServer(port: Int, rootPath: String) {
        webDavServer = WebDavServer(port, rootPath).apply {
            onLog = { message ->
                log("[WebDAV] $message", LogType.WEBDAV)
            }
            start()
        }
    }
    
    private fun stopServers() {
        try {
            ftpServer?.stop()
            ftpServer = null
            
            webDavServer?.stop()
            webDavServer = null
            
            isRunning = false
            currentIpAddress = null
            
            log("Servers stopped", LogType.INFO)
            notifyStatusChanged()
            
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            log("Error stopping servers: ${e.message}", LogType.ERROR)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(ipAddress: String, ftpPort: Int, webDavPort: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, FtpServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val contentText = "FTP: $ftpPort | WebDAV: $webDavPort"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("FTP: ftp://$ipAddress:$ftpPort\nWebDAV: http://$ipAddress:$webDavPort"))
            .setSmallIcon(R.drawable.ic_server)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_server), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    fun addLogListener(listener: LogListener) {
        logListeners.add(listener)
    }
    
    fun removeLogListener(listener: LogListener) {
        logListeners.remove(listener)
    }
    
    fun addStatusListener(listener: StatusListener) {
        statusListeners.add(listener)
    }
    
    fun removeStatusListener(listener: StatusListener) {
        statusListeners.remove(listener)
    }
    
    private fun log(message: String, type: LogType) {
        val timestamp = timeFormat.format(Date())
        val logMessage = "[$timestamp] $message"
        
        logListeners.forEach { it.onLog(logMessage, type) }
    }
    
    private fun notifyStatusChanged() {
        statusListeners.forEach {
            it.onStatusChanged(isRunning, currentIpAddress, currentPort, currentWebDavPort)
        }
    }
    
    override fun onDestroy() {
        stopServers()
        super.onDestroy()
    }
}
