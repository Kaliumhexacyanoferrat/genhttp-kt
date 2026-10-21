package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode

internal class ClasspathResourceNode(
    source: ClassLoader,
    prefix: String,
    content: ClasspathContent,
    override val parent: ResourceContainer,
    override val name: String
) : ClasspathResourceContainer(source, prefix, content), ResourceNode
