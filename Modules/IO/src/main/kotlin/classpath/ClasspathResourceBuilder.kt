package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.io.guessContentType

import java.time.Instant

import kotlin.reflect.KClass

/**
 * Builds a resource that is read from the classpath.
 */
public class ClasspathResourceBuilder : ResourceBuilder<ClasspathResourceBuilder> {

    private var loader: ClassLoader? = null

    private var anchor: KClass<*>? = null

    private var path: String? = null

    private var name: String? = null

    private var contentType: ContentType? = null

    private var modified: Instant? = null

    /**
     * Sets the class loader used to look up the resource (defaults to the class loader of the [anchor], if set,
     * otherwise to the context class loader of the current thread).
     *
     * @param loader The class loader to search the resource with
     */
    public fun loader(loader: ClassLoader): ClasspathResourceBuilder {
        this.loader = loader
        return this
    }

    /**
     * Resolves relative paths from the package of the given class and uses its class loader
     * (unless a loader has been set explicitly).
     *
     * @param anchor The class to resolve the path from
     */
    public fun anchor(anchor: KClass<*>): ClasspathResourceBuilder {
        this.anchor = anchor
        return this
    }

    /**
     * Sets the path of the resource. If an [anchor] has been set, the path is resolved relative to its package,
     * unless it starts with a slash. Without an anchor, the path is absolute (the leading slash is optional).
     *
     * @param name The path of the resource (e.g. "web/index.html")
     */
    public fun path(name: String): ClasspathResourceBuilder {
        this.path = name
        return this
    }

    override fun name(name: String): ClasspathResourceBuilder {
        this.name = name
        return this
    }

    override fun type(contentType: ContentType): ClasspathResourceBuilder {
        this.contentType = contentType
        return this
    }

    override fun modified(modified: Instant): ClasspathResourceBuilder {
        this.modified = modified
        return this
    }

    override fun build(): Resource {
        val path = path ?: throw BuilderMissingPropertyException("path")

        val anchor = anchor?.java

        val loader = loader ?: anchor?.classLoader ?: defaultClassLoader()

        val absolute = if (anchor != null && !path.startsWith("/")) {
            joinClasspathPath(anchor.packagePath(), normalizeClasspathPath(path))
        } else {
            normalizeClasspathPath(path)
        }

        val url = ClasspathResource.locate(loader, absolute)

        val modified = modified ?: url.getModificationDate()

        val type = contentType ?: path.guessContentType() ?: ContentType.ApplicationForceDownload

        return ClasspathResource(loader, absolute, url, name, type, modified)
    }

}
