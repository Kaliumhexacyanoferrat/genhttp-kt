package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.ResourceTree

internal class ClasspathResourceTree(source: ClassLoader, root: String) :
    ClasspathResourceContainer(source, root, source.scan(root)), ResourceTree
