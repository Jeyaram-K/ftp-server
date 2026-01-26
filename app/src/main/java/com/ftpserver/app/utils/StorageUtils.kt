package com.ftpserver.app.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

object StorageUtils {
    
    fun getDefaultStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    
    fun getStorageVolumes(context: Context): List<StorageVolume> {
        val volumes = mutableListOf<StorageVolume>()
        
        // Add primary storage
        val primaryPath = Environment.getExternalStorageDirectory()
        volumes.add(
            StorageVolume(
                name = "Internal Storage",
                path = primaryPath.absolutePath,
                isRemovable = false,
                isPrimary = true
            )
        )
        
        // Try to get external SD cards
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumes = storageManager.storageVolumes
                
                for (volume in storageVolumes) {
                    if (volume.isRemovable) {
                        val uuid = volume.uuid
                        if (uuid != null) {
                            val path = "/storage/$uuid"
                            if (File(path).exists()) {
                                volumes.add(
                                    StorageVolume(
                                        name = volume.getDescription(context) ?: "SD Card",
                                        path = path,
                                        isRemovable = true,
                                        isPrimary = false
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        return volumes
    }
    
    fun isStorageAccessible(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.canRead()
    }
    
    fun getAvailableSpace(path: String): Long {
        return try {
            val file = File(path)
            file.freeSpace
        } catch (e: Exception) {
            0L
        }
    }
    
    fun getTotalSpace(path: String): Long {
        return try {
            val file = File(path)
            file.totalSpace
        } catch (e: Exception) {
            0L
        }
    }
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000_000L -> String.format("%.2f TB", bytes / 1_000_000_000_000.0)
            bytes >= 1_000_000_000L -> String.format("%.2f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> String.format("%.2f MB", bytes / 1_000_000.0)
            bytes >= 1_000L -> String.format("%.2f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
    
    data class StorageVolume(
        val name: String,
        val path: String,
        val isRemovable: Boolean,
        val isPrimary: Boolean
    )
}
