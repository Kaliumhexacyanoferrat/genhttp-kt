package org.genhttp.modules.conversion

import org.genhttp.modules.conversion.formatters.BoolFormatter
import org.genhttp.modules.conversion.formatters.DateFormatter
import org.genhttp.modules.conversion.formatters.EnumFormatter
import org.genhttp.modules.conversion.formatters.FormattableFormatter
import org.genhttp.modules.conversion.formatters.FormatterBuilder
import org.genhttp.modules.conversion.formatters.StringFormatter

/**
 * Entry point to customize the string representation generated for
 * various types that can be returned or read by services.
 */
public object Formatting {

    /**
     * The default formatters to be used with support for enums, GUIDs,
     * primitive types and strings.
     *
     * @return The default formatters
     */
    public fun default(): FormatterBuilder = FormatterBuilder().add<StringFormatter>()
        .add<EnumFormatter>()
        .add<BoolFormatter>()
        .add<DateFormatter>()
        .add<FormattableFormatter>()

    /**
     * Creates an empty formatter registry that can be extended
     * as needed.
     *
     * @return An empty formatter registry
     */
    public fun empty(): FormatterBuilder = FormatterBuilder()

}
