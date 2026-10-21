package org.genhttp.modules.conversion.serializers

import tools.jackson.databind.PropertyNamingStrategies

/**
 * Converts property names like the camel case policy of System.Text.Json: the leading upper case
 * characters are converted to lower case (`Name` becomes `name`, `URLValue` becomes `urlValue`).
 */
internal object CamelCaseNaming : PropertyNamingStrategies.NamingBase() {

    override fun translate(propertyName: String): String {
        if (propertyName.isEmpty() || !propertyName[0].isUpperCase()) {
            return propertyName
        }

        val chars = propertyName.toCharArray()

        for (i in chars.indices) {
            if (i == 1 && !chars[i].isUpperCase()) {
                break
            }

            if (i > 0 && i + 1 < chars.size && !chars[i + 1].isUpperCase()) {
                // keep the upper case character starting the next word (unless it is a separator)
                if (chars[i + 1] == ' ') {
                    chars[i] = chars[i].lowercaseChar()
                }

                break
            }

            chars[i] = chars[i].lowercaseChar()
        }

        return String(chars)
    }

}
