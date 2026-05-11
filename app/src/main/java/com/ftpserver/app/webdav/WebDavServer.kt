package com.ftpserver.app.webdav

import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WebDavServer(
    private val port: Int,
    private val rootPath: String
) : NanoHTTPD(port) {
    
    private val rootDir = File(rootPath)
    private val rootDirCanonical = rootDir.canonicalPath
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }
    private val mimeTypeCache = HashMap<String, String>()
    
    var onLog: ((String) -> Unit)? = null
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method
        
        onLog?.invoke("${method.name} $uri")
        
        return try {
            when (method) {
                Method.GET -> handleGet(uri)
                Method.PUT -> handlePut(session, uri)
                Method.DELETE -> handleDelete(uri)
                Method.PROPFIND -> handlePropfind(uri, session)
                Method.PROPPATCH -> handleProppatch(uri)
                Method.MKCOL -> handleMkcol(uri)
                Method.COPY -> handleCopy(session, uri)
                Method.MOVE -> handleMove(session, uri)
                Method.OPTIONS -> handleOptions()
                Method.HEAD -> handleHead(uri)
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed")
            }
        } catch (e: Exception) {
            onLog?.invoke("Error: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}")
        }
    }
    
    private fun handleGet(uri: String): Response {
        val file = getFile(uri)
        
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        
        if (file.isDirectory) {
            return handleDirectoryListing(file, uri)
        }
        
        val mimeType = getMimeType(file.name)
        val fis = BufferedInputStream(FileInputStream(file), 65536)
        val response = newFixedLengthResponse(Response.Status.OK, mimeType, fis, file.length())
        response.addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }
    
    private fun handleDirectoryListing(dir: File, uri: String): Response {
        val html = buildString {
            append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            append("<title>Index of $uri</title>")
            append("<style>body{font-family:sans-serif;padding:20px}a{text-decoration:none;color:#1976D2}a:hover{text-decoration:underline}table{border-collapse:collapse;width:100%}td,th{padding:8px;text-align:left;border-bottom:1px solid #ddd}</style>")
            append("</head><body>")
            append("<h1>Index of $uri</h1>")
            append("<table><tr><th>Name</th><th>Size</th><th>Modified</th></tr>")
            
            if (uri != "/") {
                append("<tr><td><a href='${getParentUri(uri)}'>..</a></td><td>-</td><td>-</td></tr>")
            }
            
            (dir.listFiles() ?: emptyArray()).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { file ->
                val name = if (file.isDirectory) "${file.name}/" else file.name
                val size = if (file.isDirectory) "-" else formatSize(file.length())
                val modified = dateFormat.format(Date(file.lastModified()))
                val href = if (uri.endsWith("/")) "$uri${file.name}" else "$uri/${file.name}"
                append("<tr><td><a href='$href'>$name</a></td><td>$size</td><td>$modified</td></tr>")
            }
            
            append("</table></body></html>")
        }
        
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
    
    private fun handlePut(session: IHTTPSession, uri: String): Response {
        val file = getFile(uri)
        file.parentFile?.mkdirs()
        
        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0
        
        session.inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output, bufferSize = 65536)
            }
        }
        
        return if (file.exists()) {
            newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Created")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to create file")
        }
    }
    
    private fun handleDelete(uri: String): Response {
        val file = getFile(uri)
        
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        
        return if (deleted) {
            newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to delete")
        }
    }
    
    private fun handlePropfind(uri: String, session: IHTTPSession): Response {
        val file = getFile(uri)
        
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        
        val depth = session.headers["depth"] ?: "1"
        val xml = buildPropfindResponse(file, uri, depth)
        
        val response = newFixedLengthResponse(Response.Status.lookup(207), "application/xml; charset=utf-8", xml)
        response.addHeader("DAV", "1, 2")
        return response
    }
    
    private fun buildPropfindResponse(file: File, uri: String, depth: String): String {
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<D:multistatus xmlns:D=\"DAV:\">")
            
            appendFileResponse(this, file, uri)
            
            if (file.isDirectory && depth != "0") {
                (file.listFiles() ?: emptyArray()).forEach { child ->
                    val childUri = if (uri.endsWith("/")) "$uri${child.name}" else "$uri/${child.name}"
                    appendFileResponse(this, child, childUri)
                }
            }
            
            append("</D:multistatus>")
        }
    }
    
    private fun appendFileResponse(sb: StringBuilder, file: File, uri: String) {
        val encodedUri = uri.replace(" ", "%20")
        val isDir = file.isDirectory
        val lastModified = dateFormat.format(Date(file.lastModified()))
        val contentLength = if (isDir) 0 else file.length()
        val mimeType = if (isDir) "httpd/unix-directory" else getMimeType(file.name)
        
        sb.append("<D:response>")
        sb.append("<D:href>$encodedUri${if (isDir && !uri.endsWith("/")) "/" else ""}</D:href>")
        sb.append("<D:propstat>")
        sb.append("<D:prop>")
        sb.append("<D:displayname>${file.name}</D:displayname>")
        sb.append("<D:getcontentlength>$contentLength</D:getcontentlength>")
        sb.append("<D:getlastmodified>$lastModified</D:getlastmodified>")
        sb.append("<D:getcontenttype>$mimeType</D:getcontenttype>")
        
        if (isDir) {
            sb.append("<D:resourcetype><D:collection/></D:resourcetype>")
        } else {
            sb.append("<D:resourcetype/>")
        }
        
        sb.append("</D:prop>")
        sb.append("<D:status>HTTP/1.1 200 OK</D:status>")
        sb.append("</D:propstat>")
        sb.append("</D:response>")
    }
    
    private fun handleProppatch(uri: String): Response {
        val file = getFile(uri)
        
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        
        // Simple PROPPATCH response - we don't actually store custom properties
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
                <D:response>
                    <D:href>${uri.replace(" ", "%20")}</D:href>
                    <D:propstat>
                        <D:prop/>
                        <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                </D:response>
            </D:multistatus>""".trimIndent()
        
        return newFixedLengthResponse(Response.Status.lookup(207), "application/xml; charset=utf-8", xml)
    }
    
    private fun handleMkcol(uri: String): Response {
        val dir = getFile(uri)
        
        if (dir.exists()) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Already exists")
        }
        
        return if (dir.mkdirs()) {
            newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Created")
        } else {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to create directory")
        }
    }
    
    private fun handleCopy(session: IHTTPSession, uri: String): Response {
        val source = getFile(uri)
        val destination = session.headers["destination"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No destination")
        
        if (!source.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Source not found")
        }
        
        val destUri = destination.substringAfter("//").substringAfter("/")
        val destFile = getFile("/$destUri")
        
        return try {
            source.copyRecursively(destFile, overwrite = true)
            newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Copied")
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Copy failed: ${e.message}")
        }
    }
    
    private fun handleMove(session: IHTTPSession, uri: String): Response {
        val source = getFile(uri)
        val destination = session.headers["destination"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No destination")
        
        if (!source.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Source not found")
        }
        
        val destUri = destination.substringAfter("//").substringAfter("/")
        val destFile = getFile("/$destUri")
        
        return try {
            if (source.renameTo(destFile)) {
                newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Moved")
            } else {
                // Fallback: copy then delete
                source.copyRecursively(destFile, overwrite = true)
                source.deleteRecursively()
                newFixedLengthResponse(Response.Status.CREATED, MIME_PLAINTEXT, "Moved")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Move failed: ${e.message}")
        }
    }
    
    private fun handleOptions(): Response {
        val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        response.addHeader("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, PROPFIND, PROPPATCH, MKCOL, COPY, MOVE")
        response.addHeader("DAV", "1, 2")
        response.addHeader("MS-Author-Via", "DAV")
        return response
    }
    
    private fun handleHead(uri: String): Response {
        val file = getFile(uri)
        
        if (!file.exists()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        }
        
        val mimeType = if (file.isDirectory) "httpd/unix-directory" else getMimeType(file.name)
        val response = newFixedLengthResponse(Response.Status.OK, mimeType, "")
        response.addHeader("Content-Length", file.length().toString())
        response.addHeader("Last-Modified", dateFormat.format(Date(file.lastModified())))
        return response
    }
    
    private fun getFile(uri: String): File {
        val path = uri.trimStart('/').replace("%20", " ")
        val file = if (path.isEmpty()) rootDir else File(rootDir, path)
        return if (isWithinRoot(file)) file else rootDir
    }
    
    private fun isWithinRoot(file: File): Boolean {
        return try {
            file.canonicalPath.startsWith(rootDirCanonical)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getParentUri(uri: String): String {
        val trimmed = uri.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash <= 0) "/" else trimmed.substring(0, lastSlash)
    }
    
    private fun getMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return mimeTypeCache.getOrPut(ext) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
