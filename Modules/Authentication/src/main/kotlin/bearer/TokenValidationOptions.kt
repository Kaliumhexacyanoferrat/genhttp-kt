package org.genhttp.modules.authentication.bearer

import com.nimbusds.jwt.SignedJWT

import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request

import java.security.Key

internal class TokenValidationOptions {

    internal var audience: String? = null

    internal var issuer: String? = null

    internal var lifetime: Boolean = true

    internal var customValidator: (suspend (SignedJWT) -> Unit)? = null

    internal var userMapping: (suspend (Request, SignedJWT) -> User?)? = null

    internal var customKeyResolver: (suspend (SignedJWT) -> Collection<Key>)? = null

}
