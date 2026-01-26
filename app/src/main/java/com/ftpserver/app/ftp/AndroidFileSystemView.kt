package com.ftpserver.app.ftp

import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpFile
import org.apache.ftpserver.ftplet.User
import java.io.File

class AndroidFileSystemView(
    private val rootDir: File,
    private val user: User
) : FileSystemView {
    
    private var currentDir: File = rootDir
    
    override fun getHomeDirectory(): FtpFile {
        return AndroidFtpFile(rootDir, rootDir, user)
    }
    
    override fun getWorkingDirectory(): FtpFile {
        return AndroidFtpFile(currentDir, rootDir, user)
    }
    
    override fun changeWorkingDirectory(dir: String): Boolean {
        val newDir = resolveFile(dir)
        return if (newDir.exists() && newDir.isDirectory && isWithinRoot(newDir)) {
            currentDir = newDir
            true
        } else {
            false
        }
    }
    
    override fun getFile(file: String): FtpFile {
        val resolvedFile = resolveFile(file)
        return AndroidFtpFile(resolvedFile, rootDir, user)
    }
    
    override fun isRandomAccessible(): Boolean = true
    
    override fun dispose() {
        // Nothing to dispose
    }
    
    private fun resolveFile(path: String): File {
        return when {
            path.startsWith("/") -> {
                // Absolute path from root
                File(rootDir, path.trimStart('/'))
            }
            path == ".." -> {
                val parent = currentDir.parentFile
                if (parent != null && isWithinRoot(parent)) parent else currentDir
            }
            path == "." -> currentDir
            else -> File(currentDir, path)
        }.canonicalFile
    }
    
    private fun isWithinRoot(file: File): Boolean {
        return try {
            file.canonicalPath.startsWith(rootDir.canonicalPath)
        } catch (e: Exception) {
            false
        }
    }
}
