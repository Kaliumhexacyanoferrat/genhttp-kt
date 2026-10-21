# glyph11-kt API specification

Kotlin API of the Glyph11 port (`Glyph11/`, artifact `org.genhttp:glyph11-kt`). Upstream: `../Glyph11/src/Glyph11`
(managed parser only — the native and pico bindings are not ported). This spec is binding for both the implementation
and the ported tests.

## General rules

- `ReadOnlyMemory<byte>` / `ReadOnlySpan<byte>` → `java.nio.ByteBuffer`. Inputs are read from `position` until `limit`;
  all offsets in results are **relative to the input's position**. Parsers never modify the position/limit of the
  input. Slices stored in results are read-only `ByteBuffer`s whose content is `position..limit` (create them with
  `input.slice(input.position() + offset, length).asReadOnlyBuffer()` or equivalent).
- Only contiguous input is supported. `ReadOnlySequence` overloads and `BufferSegment` are not ported.
- `bool TryX(..., out int count)` becomes `fun tryX(...): Int` returning the count, or `-1` for `false`.
- Pooling (`ArrayPool`) is not ported; `close()` exists for API parity and releases internal storage.
- Lookup tables (`SearchValues`) become `BooleanArray(256)` tables.
- Upstream XML docs become KDoc. `explicitApi()` is on.

## Packages and declarations

### `org.genhttp.glyph11`

```kotlin
public open class HttpParseException(message: String, public val statusCode: Int = 400) : RuntimeException(message) {
    public val isLimitViolation: Boolean // statusCode != 400
}
```

### `org.genhttp.glyph11.protocol`

```kotlin
public class KeyValueList(initialCapacity: Int = 16) : AutoCloseable {
    public val count: Int
    public operator fun get(index: Int): Pair<ByteBuffer, ByteBuffer>   // IndexOutOfBoundsException if out of range
    internal fun add(key: ByteBuffer, value: ByteBuffer)
    internal fun clear()
    public fun asList(): List<Pair<ByteBuffer, ByteBuffer>>               // AsSpan()
    override fun close()
}

public class BinaryRequest : AutoCloseable {
    public var version: ByteBuffer   (internal set)  // empty buffer by default
    public var method: ByteBuffer    (internal set)
    public var path: ByteBuffer      (internal set)
    public val queryParameters: KeyValueList
    public val headers: KeyValueList
    public var body: ByteBuffer      (internal set)
    public fun clear()
    override fun close()
}

public class BinaryResponse : AutoCloseable {
    public var version: ByteBuffer        (internal set)
    public var statusCode: ByteBuffer     (internal set)
    public var status: Int                (internal set)
    public var reasonPhrase: ByteBuffer   (internal set)
    public val headers: KeyValueList
    public var body: ByteBuffer           (internal set)
    public fun clear()
    override fun close()
}
```

### `org.genhttp.glyph11.parser`

```kotlin
public data class ParserLimits(
    val maxHeaderCount: Int = 100,
    val maxHeaderNameLength: Int = 256,
    val maxHeaderValueLength: Int = 8192,
    val maxUrlLength: Int = 8192,
    val maxQueryParameterCount: Int = 128,
    val maxMethodLength: Int = 16,
    val maxReasonPhraseLength: Int = 512,
    val maxTotalHeaderBytes: Int = 1_048_576,
) {
    public companion object { public val Default: ParserLimits }
}
// C# `ParserLimits.Default with { MaxHeaderCount = 2 }` → `ParserLimits.Default.copy(maxHeaderCount = 2)`

public object ParserConstants {
    public val Crlf: ByteArray; public val CrlfCrlf: ByteArray
    public const val Space: Byte; Question; QuerySeparator; Equal; Colon
    public val TransferEncodingName: ByteArray; public val ChunkedValue: ByteArray
    public fun isValidToken(span: ByteBuffer): Boolean
    public fun isValidFieldValue(span: ByteBuffer): Boolean
    public fun isValidHttpVersion(span: ByteBuffer): Boolean
    public fun isDigit(b: Byte): Boolean
    public fun isValidRequestTarget(span: ByteBuffer): Boolean
    public fun asciiEqualsIgnoreCase(a: ByteBuffer, b: ByteBuffer): Boolean
    // plus the lookup tables / builders that upstream exposes publicly, as BooleanArray / ByteArray
}

public enum class ChunkResult { NeedMoreData, Chunk, Completed }

/** Stateful streaming parser for chunked transfer encoding (upstream: mutable struct). */
public class ChunkedBodyStream {
    /** Bytes consumed from the input by the last call. */
    public val bytesConsumed: Int
    /** Offset (relative to input position) of the chunk payload of the last call (valid for Chunk). */
    public val dataOffset: Int
    /** Length of the chunk payload of the last call (valid for Chunk). */
    public val dataLength: Int
    public fun tryReadChunk(input: ByteBuffer): ChunkResult
    /** Returns a copy of the current state (replaces C# struct copy semantics). */
    public fun copy(): ChunkedBodyStream
}
```

`org.genhttp.glyph11.parser.flexibleparser` (upstream namespace `Glyph11.Parser.FlexibleParser`):

```kotlin
public object FlexibleParser {
    public fun tryExtractFullHeader(input: ByteBuffer, request: BinaryRequest): Int   // TryExtractFullHeaderReadOnlyMemory
}
```

`org.genhttp.glyph11.parser.ultrahardened`:

```kotlin
public object UltraHardenedParser {
    public fun tryExtractFullHeader(input: ByteBuffer, request: BinaryRequest, limits: ParserLimits = ParserLimits.Default): Int          // TryExtractFullHeaderROM / ...Validated
    public fun tryExtractFullResponseHeader(input: ByteBuffer, response: BinaryResponse, limits: ParserLimits = ParserLimits.Default): Int  // TryExtractFullResponseHeaderROM / ...Validated
}
```

The returned count has exactly the semantics of upstream's `bytesReadCount` (upstream callers advance by
`bytesReadCount + 1`).

### `org.genhttp.glyph11.validation`

```kotlin
public enum class BodyFraming { None, ContentLength, Chunked, UntilClose }

public class BodyFramingResult private constructor(public val framing: BodyFraming, public val contentLength: Long) {
    public companion object {
        public val NoBody: BodyFramingResult
        public fun forContentLength(length: Long): BodyFramingResult
        public val ForChunked: BodyFramingResult
        public val ForUntilClose: BodyFramingResult
    }
}

public object BodyFramingDetector {
    public fun detectBodyFraming(request: BinaryRequest): BodyFramingResult
    public fun detectResponseBodyFraming(response: BinaryResponse, requestMethod: ByteBuffer): BodyFramingResult
}

public object ChunkedBodyValidator {
    public fun tryValidate(body: ByteBuffer): Int   // bytesConsumed or -1 (incomplete)
}

public object ContentLengthBodyReader {
    public fun hasCompleteBody(body: ByteBuffer, contentLength: Long): Boolean
    public fun parseContentLength(request: BinaryRequest): Long
}
```

## Tests

Upstream xUnit tests (`../Glyph11/tests/Tests`) are ported to JUnit 6 in `Glyph11/src/test/kotlin`, package
`org.genhttp.glyph11.tests`, one Kotlin class per upstream class. C# partial classes spread over several files
(e.g. `UltraHardenedParserTests`) become a single Kotlin class in a single file.

- `[Fact]` → `@Test`, `[Theory]` + `[InlineData]` → `@ParameterizedTest` + `@ValueSource`/`@CsvSource`/`@MethodSource`
  with the same data rows.
- Method names keep the upstream name with the first letter lower-cased (`Throws_WhenHeaderCountExceedsLimit` →
  `throws_WhenHeaderCountExceedsLimit`).
- Theories with a `bool multi` / `multiSegment` parameter keep the parameter: `false` parses a plain heap buffer,
  `true` parses the same bytes placed in the middle of a larger **direct** buffer (non-zero position, extra bytes after
  the limit) — this verifies that offsets are relative to the position.
- `ChunkedBodyStreamSequenceTests` (ReadOnlySequence segmentation) is not ported (listed in
  `tools/test_parity_exclusions.txt`).
- `Assert.Throws<HttpParseException>` → `assertFailsWith<HttpParseException>`, `Assert.Equal(a, b)` → `assertEquals(a, b)`
  etc. Byte content comparisons use a helper equivalent to upstream `AssertAscii`.
