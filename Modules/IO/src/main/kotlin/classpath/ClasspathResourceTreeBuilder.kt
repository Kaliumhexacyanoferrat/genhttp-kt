package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a resource tree that provides the content of a directory of the classpath. The content of the directory
 * is gathered from all classpath roots (directories and archives) that provide it.
 */
public class ClasspathResourceTreeBuilder : Builder<ResourceTree> {

    private var root: String? = null

    private var loader: ClassLoader? = null

    /**
     * Sets the class loader used to look up the content (defaults to the context class loader of the current thread).
     *
     * @param loader The class loader to enumerate the content with
     */
    public fun loader(loader: ClassLoader): ClasspathResourceTreeBuilder {
        this.loader = loader
        return this
    }

    /**
     * Sets the classpath directory to be provided.
     *
     * @param root The absolute path of the directory (e.g. "web/assets", the leading slash is optional)
     */
    public fun root(root: String): ClasspathResourceTreeBuilder {
        this.root = root
        return this
    }

    override fun build(): ResourceTree {
        val root = root ?: throw BuilderMissingPropertyException("root")

        val loader = loader ?: defaultClassLoader()

        return ClasspathResourceTree(loader, normalizeClasspathPath(root))
    }

}
