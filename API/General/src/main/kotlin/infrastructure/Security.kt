package org.genhttp.api.infrastructure

/**
 * The TLS protocol versions an endpoint may negotiate (`System.Security.Authentication.SslProtocols` in .NET).
 *
 * @property protocolName The name of the protocol as used by the JSSE (e.g. "TLSv1.3")
 */
public enum class SslProtocol(public val protocolName: String) {

    /** TLS 1.2 */
    Tls12("TLSv1.2"),

    /** TLS 1.3 */
    Tls13("TLSv1.3")

}

/**
 * Errors detected while validating a certificate presented by the remote party
 * (`System.Net.Security.SslPolicyErrors` in .NET).
 */
public enum class SslPolicyError {

    /** The remote party did not present a certificate. */
    RemoteCertificateNotAvailable,

    /** The name of the certificate does not match the host. */
    RemoteCertificateNameMismatch,

    /** The certificate chain could not be validated (e.g. untrusted root, expired, revoked). */
    RemoteCertificateChainErrors

}

/**
 * Specifies how certificates are checked for revocation (`System.Security.Cryptography.X509Certificates.X509RevocationMode` in .NET).
 */
public enum class X509RevocationMode {

    /** No revocation check is performed. */
    NoCheck,

    /** A revocation check is performed using an online certificate revocation list (or OCSP). */
    Online,

    /** A revocation check is performed using cached revocation information only. */
    Offline

}
