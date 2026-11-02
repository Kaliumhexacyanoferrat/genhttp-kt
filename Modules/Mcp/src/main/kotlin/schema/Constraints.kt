package org.genhttp.modules.mcp.schema

import jakarta.validation.constraints.AssertFalse
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.FutureOrPresent
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Negative
import jakarta.validation.constraints.NegativeOrZero
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Year
import java.time.YearMonth
import java.time.chrono.ChronoLocalDate
import java.time.chrono.ChronoLocalDateTime
import java.time.chrono.ChronoZonedDateTime
import java.util.Calendar
import java.util.Date

import kotlin.reflect.KParameter

import jakarta.validation.constraints.Pattern as PatternConstraint

/**
 * Translates the constraints of Jakarta Bean Validation (such as [Min]) into the corresponding keywords of a JSON
 * schema, and checks values against them (the validation attributes of .NET upstream, which validate
 * themselves).
 *
 * Values are checked without a validation provider: the built-in constraints of `jakarta.validation.constraints`
 * are evaluated by this module and reported with the default messages of Bean Validation.
 */
internal object Constraints {

    private const val ConstraintPackage = "jakarta.validation.constraints"

    private const val DefaultMessagePrefix = "{$ConstraintPackage."

    private val Placeholder = Regex("\\{([A-Za-z]+)}")

    // region Discovery

    /**
     * Collects the constraints declared by the given annotations (without duplicates).
     */
    fun find(vararg providers: List<Annotation>?): List<Annotation> {
        val found = mutableListOf<Annotation>()

        for (provider in providers) {
            if (provider == null) {
                continue
            }

            for (annotation in provider) {
                for (constraint in unwrap(annotation)) {
                    if (constraint !in found) {
                        found.add(constraint)
                    }
                }
            }
        }

        return found
    }

    /**
     * Collects the constraints declared on the given parameter.
     */
    fun find(parameter: KParameter): List<Annotation> = find(parameter.annotations)

    /**
     * Returns the constraints represented by the given annotation (the annotation itself or the entries of a
     * container of repeated constraints, such as `Min.List`).
     */
    private fun unwrap(annotation: Annotation): List<Annotation> {
        val type = annotation.annotationClass.java

        if (type.`package`?.name == ConstraintPackage && type.enclosingClass == null) {
            return listOf(annotation)
        }

        if (type.simpleName == "List" && type.enclosingClass?.`package`?.name == ConstraintPackage) {
            val entries = runCatching { type.getMethod("value").invoke(annotation) }.getOrNull()

            if (entries is Array<*>) {
                return entries.filterIsInstance<Annotation>()
            }
        }

        return emptyList()
    }

    // endregion

    // region Schema

    /**
     * Adds the keywords corresponding to the given constraints to the given schema.
     */
    fun apply(schema: ObjectNode, constraints: List<Annotation>) {
        for (constraint in constraints) {
            when (constraint) {
                is Min -> setBound(schema, "minimum", BigDecimal.valueOf(constraint.value))

                is Max -> setBound(schema, "maximum", BigDecimal.valueOf(constraint.value))

                is DecimalMin -> setBound(schema, if (constraint.inclusive) "minimum" else "exclusiveMinimum", constraint.value.toBigDecimalOrNull())

                is DecimalMax -> setBound(schema, if (constraint.inclusive) "maximum" else "exclusiveMaximum", constraint.value.toBigDecimalOrNull())

                is Size -> setLength(schema, constraint.min.takeIf { it > 0 }, constraint.max.takeIf { it < Int.MAX_VALUE })

                is PatternConstraint -> schema.put("pattern", constraint.regexp)

                is Email -> schema.put("format", "email")
            }
        }
    }

    private fun setBound(schema: ObjectNode, keyword: String, value: BigDecimal?) {
        if (value == null) {
            return
        }

        val integral = value.signum() == 0 || value.stripTrailingZeros().scale() <= 0

        if (integral && value.abs() <= MaxLong) {
            schema.put(keyword, value.toLong())
        } else {
            schema.put(keyword, value.toDouble())
        }
    }

    private fun setLength(schema: ObjectNode, minimum: Int?, maximum: Int?) {
        val isArray = hasType(schema, "array")

        if (!isArray && !hasType(schema, "string")) {
            return
        }

        if (minimum != null) {
            schema.put(if (isArray) "minItems" else "minLength", minimum)
        }

        if (maximum != null) {
            schema.put(if (isArray) "maxItems" else "maxLength", maximum)
        }
    }

    private fun hasType(schema: ObjectNode, type: String): Boolean {
        val value = schema.get("type") ?: return false

        if (value.isString) {
            return value.stringValue() == type
        }

        if (value is ArrayNode) {
            return value.any { it.isString && it.stringValue() == type }
        }

        return false
    }

    private val MaxLong = BigDecimal.valueOf(Long.MAX_VALUE)

    // endregion

    // region Validation

    /**
     * Checks the given value against the given constraint. As in Bean Validation, `null` is valid for every
     * constraint except [NotNull] and [NotBlank].
     */
    fun isValid(constraint: Annotation, value: Any?): Boolean = when (constraint) {
        is NotNull -> value != null
        is Null -> value == null
        is NotBlank -> value != null && !value.toString().isBlank()
        is NotEmpty -> value != null && (sizeOf(value)?.let { it > 0 } ?: true)
        else -> value == null || isValidValue(constraint, value)
    }

    private fun isValidValue(constraint: Annotation, value: Any): Boolean = when (constraint) {
        is AssertTrue -> value == true
        is AssertFalse -> value == false
        is Min -> compare(value, BigDecimal.valueOf(constraint.value), nanIsValid = false)?.let { it >= 0 } ?: false
        is Max -> compare(value, BigDecimal.valueOf(constraint.value), nanIsValid = false)?.let { it <= 0 } ?: false
        is DecimalMin -> constraint.value.toBigDecimalOrNull()?.let { bound -> compare(value, bound, nanIsValid = false)?.let { if (constraint.inclusive) it >= 0 else it > 0 } } ?: false
        is DecimalMax -> constraint.value.toBigDecimalOrNull()?.let { bound -> compare(value, bound, nanIsValid = false)?.let { if (constraint.inclusive) it <= 0 else it < 0 } } ?: false
        is Positive -> compare(value, BigDecimal.ZERO, nanIsValid = false)?.let { it > 0 } ?: false
        is PositiveOrZero -> compare(value, BigDecimal.ZERO, nanIsValid = false)?.let { it >= 0 } ?: false
        is Negative -> compare(value, BigDecimal.ZERO, nanIsValid = false)?.let { it < 0 } ?: false
        is NegativeOrZero -> compare(value, BigDecimal.ZERO, nanIsValid = false)?.let { it <= 0 } ?: false
        is Digits -> isValidDigits(value, constraint)
        is Size -> sizeOf(value)?.let { it >= constraint.min && it <= constraint.max } ?: true
        is PatternConstraint -> value !is CharSequence || toRegex(constraint.regexp, constraint.flags).matches(value)
        is Email -> isValidEmail(value, constraint)
        is Past -> compareToNow(value)?.let { it < 0 } ?: true
        is PastOrPresent -> compareToNow(value)?.let { it <= 0 } ?: true
        is Future -> compareToNow(value)?.let { it > 0 } ?: true
        is FutureOrPresent -> compareToNow(value)?.let { it >= 0 } ?: true
        else -> true
    }

    /**
     * Describes the violation of the given constraint (the default messages of Bean Validation, or the message
     * declared by the constraint).
     */
    fun message(constraint: Annotation): String {
        val declared = runCatching { constraint.annotationClass.java.getMethod("message").invoke(constraint) as? String }.getOrNull()

        if (declared != null && !declared.startsWith(DefaultMessagePrefix)) {
            return Placeholder.replace(declared) { match -> attribute(constraint, match.groupValues[1]) ?: match.value }
        }

        return when (constraint) {
            is AssertFalse -> "must be false"
            is AssertTrue -> "must be true"
            is DecimalMax -> "must be less than ${if (constraint.inclusive) "or equal to " else ""}${constraint.value}"
            is DecimalMin -> "must be greater than ${if (constraint.inclusive) "or equal to " else ""}${constraint.value}"
            is Digits -> "numeric value out of bounds (<${constraint.integer} digits>.<${constraint.fraction} digits> expected)"
            is Email -> "must be a well-formed email address"
            is Future -> "must be a future date"
            is FutureOrPresent -> "must be a date in the present or in the future"
            is Max -> "must be less than or equal to ${constraint.value}"
            is Min -> "must be greater than or equal to ${constraint.value}"
            is Negative -> "must be less than 0"
            is NegativeOrZero -> "must be less than or equal to 0"
            is NotBlank -> "must not be blank"
            is NotEmpty -> "must not be empty"
            is NotNull -> "must not be null"
            is Null -> "must be null"
            is Past -> "must be a past date"
            is PastOrPresent -> "must be a date in the past or in the present"
            is PatternConstraint -> "must match \"${constraint.regexp}\""
            is Positive -> "must be greater than 0"
            is PositiveOrZero -> "must be greater than or equal to 0"
            is Size -> "size must be between ${constraint.min} and ${constraint.max}"
            else -> "is invalid"
        }
    }

    private fun attribute(constraint: Annotation, name: String): String? {
        val value = runCatching { constraint.annotationClass.java.getMethod(name).invoke(constraint) }.getOrNull() ?: return null

        return if (value is Array<*>) value.contentToString() else value.toString()
    }

    private fun sizeOf(value: Any): Int? = when (value) {
        is CharSequence -> value.length
        is Collection<*> -> value.size
        is Map<*, *> -> value.size
        is Array<*> -> value.size
        else -> if (value.javaClass.isArray) java.lang.reflect.Array.getLength(value) else null
    }

    /**
     * Compares the given value with the given bound (null, if the value is not a number).
     */
    private fun compare(value: Any, bound: BigDecimal, nanIsValid: Boolean): Int? {
        val number = when (value) {
            is BigDecimal -> value
            is BigInteger -> BigDecimal(value)
            is Double, is Float -> {
                val double = (value as Number).toDouble()

                when {
                    double.isNaN() -> return if (nanIsValid) 0 else null
                    double == Double.POSITIVE_INFINITY -> return 1
                    double == Double.NEGATIVE_INFINITY -> return -1
                    else -> BigDecimal(double.toString())
                }
            }
            is Number -> BigDecimal.valueOf(value.toLong())
            is CharSequence -> value.toString().toBigDecimalOrNull() ?: return null
            else -> return null
        }

        return number.compareTo(bound)
    }

    private fun isValidDigits(value: Any, constraint: Digits): Boolean {
        val number = when (value) {
            is BigDecimal -> value
            is BigInteger -> BigDecimal(value)
            is Number -> value.toString().toBigDecimalOrNull() ?: return false
            is CharSequence -> value.toString().toBigDecimalOrNull() ?: return false
            else -> return true
        }.stripTrailingZeros()

        val integer = number.precision() - number.scale()
        val fraction = if (number.scale() < 0) 0 else number.scale()

        return constraint.integer >= integer && constraint.fraction >= fraction
    }

    /**
     * Checks an email address like .NET does: exactly one `@`, neither at the start nor at the end.
     */
    private fun isValidEmail(value: Any, constraint: Email): Boolean {
        if (value !is CharSequence) {
            return false
        }

        val text = value.toString()

        val index = text.indexOf('@')

        if (index <= 0 || index == text.length - 1 || text.indexOf('@', index + 1) >= 0) {
            return false
        }

        return constraint.regexp == ".*" || toRegex(constraint.regexp, constraint.flags).matches(text)
    }

    private fun toRegex(expression: String, flags: Array<PatternConstraint.Flag>): Regex {
        val combined = flags.fold(0) { result, flag -> result or flag.value }

        return java.util.regex.Pattern.compile(expression, combined).toRegex()
    }

    /**
     * Compares the given temporal value with the current point in time (null, if the type is not supported).
     */
    private fun compareToNow(value: Any): Int? = when (value) {
        is Instant -> value.compareTo(Instant.now())
        is Date -> value.toInstant().compareTo(Instant.now())
        is Calendar -> value.toInstant().compareTo(Instant.now())
        is OffsetDateTime -> value.toInstant().compareTo(Instant.now())
        is ChronoZonedDateTime<*> -> value.toInstant().compareTo(Instant.now())
        is ChronoLocalDateTime<*> -> value.compareTo(LocalDateTime.now())
        is ChronoLocalDate -> value.compareTo(LocalDate.now())
        is LocalTime -> value.compareTo(LocalTime.now())
        is OffsetTime -> value.compareTo(OffsetTime.now())
        is Year -> value.compareTo(Year.now())
        is YearMonth -> value.compareTo(YearMonth.now())
        is MonthDay -> value.compareTo(MonthDay.now())
        else -> null
    }

    // endregion

}
