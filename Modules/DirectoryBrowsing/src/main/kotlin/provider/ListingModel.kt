package org.genhttp.modules.directorybrowsing.provider

import org.genhttp.api.content.io.ResourceContainer

/**
 * The data rendered by the directory listing.
 *
 * @property container The container whose content is listed
 * @property hasParent Whether a link to the parent directory should be rendered
 */
public data class ListingModel(val container: ResourceContainer, val hasParent: Boolean)
