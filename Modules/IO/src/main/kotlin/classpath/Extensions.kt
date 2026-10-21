package org.genhttp.modules.io.classpath

import java.io.OutputStream
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * An entry (file) found on the classpath.
 */
internal class ClasspathEntry(val url: URL, val modified: Instant)

/**
 * The files and directories found below a classpath directory (relative to it, separated by slashes).
 */
internal class ClasspathContent {

    val files: MutableMap<String, ClasspathEntry> = LinkedHashMap()

    val directories: MutableSet<String> = LinkedHashSet()

}

/**
 * The class loader to be used if the caller did not specify one.
 */
internal fun defaultClassLoader(): ClassLoader = Thread.currentThread().contextClassLoader ?: ClasspathResource::class.java.classLoader

/**
 * Converts a path to the format expected by class loaders (no leading or trailing slashes).
 */
internal fun normalizeClasspathPath(path: String): String = path.replace('\\', '/').trim('/')

/**
 * Joins two parts of a classpath path.
 */
internal fun joinClasspathPath(first: String, second: String): String =
    if (first.isEmpty()) second else if (second.isEmpty()) first else "$first/$second"

/**
 * Determines the name of the package of the given class in the format expected by class loaders.
 */
internal fun Class<*>.packagePath(): String = packageName.replace('.', '/')

/**
 * Determines the modification date of the file or archive entry referenced by this URL.
 */
internal fun URL.getModificationDate(): Instant {
    try {
        val result = when (protocol) {
            "file" -> Files.getLastModifiedTime(Path.of(toURI())).toInstant()
            "jar" -> (openConnection() as JarURLConnection).jarEntry?.lastModifiedTime?.toInstant()
            else -> openConnection().lastModified.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        }

        return result ?: Instant.now()
    } catch (_: Exception) {
        return Instant.now()
    }
}

/**
 * Determines the length of the file or archive entry referenced by this URL.
 */
internal fun URL.getContentLength(): Long {
    try {
        val known = when (protocol) {
            "file" -> Files.size(Path.of(toURI()))
            "jar" -> (openConnection() as JarURLConnection).jarEntry?.size ?: -1
            else -> -1
        }

        if (known >= 0) {
            return known
        }
    } catch (_: Exception) {
        // fall back to counting the bytes
    }

    return openStream().use { it.transferTo(OutputStream.nullOutputStream()) }
}

/**
 * Enumerates all files and directories below the given directory across all classpath roots known to the loader.
 * If a file is available multiple times, the first one found wins (like for classpath lookups).
 */
internal fun ClassLoader.scan(root: String): ClasspathContent {
    val content = ClasspathContent()

    for (url in getResources(root)) {
        try {
            when (url.protocol) {
                "file" -> scanDirectory(Path.of(url.toURI()), content)
                "jar" -> scanArchive(url, content)
            }
        } catch (_: java.io.IOException) {
            // skip unreadable roots
        } catch (_: java.net.URISyntaxException) {
            // skip roots that cannot be mapped to a path
        }
    }

    return content
}

private fun scanDirectory(directory: Path, content: ClasspathContent) {
    if (!Files.isDirectory(directory)) {
        return
    }

    Files.walk(directory, FileVisitOption.FOLLOW_LINKS).use { walk ->
        for (path in walk) {
            val relative = directory.relativize(path).toString().replace('\\', '/')

            if (relative.isEmpty()) {
                continue
            }

            if (Files.isDirectory(path)) {
                content.directories.add(relative)
            } else {
                content.files.putIfAbsent(relative, ClasspathEntry(path.toUri().toURL(), Files.getLastModifiedTime(path).toInstant()))
            }
        }
    }
}

private fun scanArchive(url: URL, content: ClasspathContent) {
    val connection = url.openConnection() as JarURLConnection

    val prefix = (connection.entryName ?: "").let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }

    val archive = connection.jarFile

    for (entry in archive.entries()) {
        val name = entry.name

        if (name.length <= prefix.length || !name.startsWith(prefix)) {
            continue
        }

        val relative = name.substring(prefix.length)

        if (entry.isDirectory) {
            content.directories.add(relative.trimEnd('/'))
        } else {
            val encoded = URI(null, null, name, null).rawPath

            val entryUrl = URI.create("jar:${connection.jarFileURL.toExternalForm()}!/$encoded").toURL()

            content.files.putIfAbsent(relative, ClasspathEntry(entryUrl, entry.lastModifiedTime?.toInstant() ?: Instant.now()))
        }
    }
}
