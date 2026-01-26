package com.ftpserver.app.ftp

import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.User
import java.io.File

class AndroidFileSystemFactory(
    private val rootPath: String
) : FileSystemFactory {
    
    override fun createFileSystemView(user: User): FileSystemView {
        return AndroidFileSystemView(File(rootPath), user)
    }
}
