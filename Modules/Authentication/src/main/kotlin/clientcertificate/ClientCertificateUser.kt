package org.genhttp.modules.authentication.clientcertificate

import org.genhttp.api.content.authentication.User

import java.security.cert.X509Certificate

import javax.security.auth.x500.X500Principal

/**
 * A simple user implementation that can be used to store the information about a client authenticated with
 * a client certificate.
 *
 * @param certificate The certificate the client authenticated with
 * @param roles The roles of the user
 */
public class ClientCertificateUser(public val certificate: X509Certificate, vararg roles: String) : User {

    // region Get-/Setters

    override val displayName: String
        get() = certificate.subjectX500Principal.getName(X500Principal.RFC1779)

    override val roles: List<String> = roles.toList()

    // endregion

}
