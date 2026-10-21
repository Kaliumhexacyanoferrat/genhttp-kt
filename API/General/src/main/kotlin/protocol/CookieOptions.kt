package org.genhttp.api.protocol

import java.time.Instant

import kotlin.time.Duration

/**
 * The attributes that can be set on a cookie in addition to its name and value (see RFC 6265).
 *
 * @property maxAge The duration after which the client should discard the cookie. This is the modern,
 * recommended way to expire a cookie - see the remarks on [expires] for how the two attributes interact.
 *
 * @property expires The point in time after which the client should discard the cookie. This is the legacy
 * attribute for cookie expiration, kept for clients that predate [maxAge]. Prefer [maxAge] instead, as it is
 * relative to the time the cookie is received and therefore not affected by clock drift between client and
 * server. If both attributes are set, RFC 6265 requires clients that understand [maxAge] to prefer it and
 * ignore [expires].
 *
 * @property domain The hosts the cookie should be sent to. Setting this attribute also makes the cookie
 * available to subdomains of the given host, which widens the audience that can read the cookie. If left
 * unset, the cookie is only sent back to the exact host that originally set it (and not to subdomains),
 * which is the safer default for most use cases.
 *
 * @property path The path that must be present in the request URL for the cookie to be sent. If left unset,
 * no Path attribute is sent and the cookie is scoped by the client according to its own default behavior
 * (typically the path of the request).
 *
 * @property secure Whether the cookie should only be sent to the server via encrypted connections. Should be
 * set whenever possible to prevent the cookie from being exposed over plain HTTP. This is also a requirement
 * for [SameSite.None], which most clients will otherwise reject.
 *
 * @property httpOnly Whether the cookie should be hidden from client side scripts. Should be set whenever the
 * cookie does not need to be read by JavaScript, as it mitigates the impact of cross-site scripting (XSS)
 * attacks by keeping session identifiers and similar values out of reach of scripts.
 *
 * @property sameSite Restricts whether the cookie is sent along with cross-site requests. Leaving this unset
 * relies on the client's default behavior, which varies across clients and may change over time (most current
 * browsers default to [SameSite.Lax]). Set this explicitly to get a predictable, future-proof outcome. Note
 * that [SameSite.None] additionally requires [secure] to be set.
 */
public data class CookieOptions(
    val maxAge: Duration? = null,
    val expires: Instant? = null,
    val domain: ByteString? = null,
    val path: ByteString? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val sameSite: SameSite? = null
)
