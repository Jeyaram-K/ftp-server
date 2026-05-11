package com.ftpserver.app.ftp

import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

class AndroidFtpFile(
    private val file: File,
    private val rootDir: File,
    private val user: User
) : FtpFile {
    
    override fun getAbsolutePath(): String {
        val rootPath = rootDir.canonicalPath
        val filePath = file.canonicalPath
        
        return if (filePath == rootPath) {
            "/"
        } else if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length).replace('\\', '/')
        } else {
            "/"
        }
    }
    
    override fun getName(): String = file.name.ifEmpty { "/" }
    
    override fun isHidden(): Boolean = file.isHidden
    
    override fun isDirectory(): Boolean = file.isDirectory
    
    override fun isFile(): Boolean = file.isFile
    
    override fun doesExist(): Boolean = file.exists()
    
    override fun isReadable(): Boolean = file.canRead()
    
    override fun isWritable(): Boolean = file.canWrite()
    
    override fun isRemovable(): Boolean = file.canWrite() && file.parentFile?.canWrite() == true
    
    override fun getOwnerName(): String = user.name ?: "user"
    
    override fun getGroupName(): String = "users"
    
    override fun getLinkCount(): Int = if (file.isDirectory) 3 else 1
    
    override fun getLastModified(): Long = file.lastModified()
    
    override fun setLastModified(time: Long): Boolean {
        return file.setLastModified(time)
    }
    
    override fun getSize(): Long = if (file.isFile) file.length() else 0L
    
    override fun getPhysicalFile(): Any = file
    
    override fun mkdir(): Boolean {
        return if (!file.exists()) {
            file.mkdirs()
        } else {
            false
        }
    }
    
    override fun delete(): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }
    
    override fun move(destination: FtpFile): Boolean {
        return try {
            val destFile = (destination as AndroidFtpFile).file
            file.renameTo(destFile)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun listFiles(): MutableList<out FtpFile>? {
        if (!file.isDirectory) return null
        
        val files = file.listFiles() ?: return mutableListOf()
        return files.map { AndroidFtpFile(it, rootDir, user) }.toMutableList()
    }
    
    override fun createOutputStream(offset: Long): OutputStream? {
        return try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            
            if (offset == 0L) {
                FileOutputStream(file)
            } else {
                val raf = RandomAccessFile(file, "rw")
                raf.seek(offset)
                Channels.newOutputStream(raf.channel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun createInputStream(offset: Long): InputStream? {
        return try {
            if (!file.exists() || !file.isFile) return null
            
            BufferedInputStream(FileInputStream(file), 65536).also {
                if (offset > 0) {
                    it.skip(offset)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
