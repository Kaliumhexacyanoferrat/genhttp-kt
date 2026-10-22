package org.genhttp.modules.i18n

import org.genhttp.modules.i18n.provider.LocalizationConcernBuilder

/**
 * Entry point to create concerns that localize requests.
 */
public object Localization {

    /**
     * Creates a localization handler that parses and sets [java.util.Locale] based on defined rules.
     *
     * By default, the culture is read from the `Accept-Language` request header and is made available as the
     * current UI culture (see [CurrentCulture]).
     *
     * @return The newly created localization concern builder
     */
    public fun create(): LocalizationConcernBuilder = LocalizationConcernBuilder()

}
