package org.genhttp.modules.authentication.clientcertificate

import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request

import java.security.cert.X509Certificate

internal class ClientCertificateAuthenticationOptions {

    internal var authorizer: (suspend (Request, X509Certificate?) -> Boolean)? = null

    internal var userMapping: (suspend (Request, X509Certificate?) -> User?)? = null

}
