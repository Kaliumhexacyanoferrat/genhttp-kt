package org.genhttp.api.content.authentication

/**
 * Information about a user that is associated with the currently handled request.
 */
public interface User {

    /**
     * The name of the user as it should be shown on the UI or in log files.
     */
    public val displayName: String

    /**
     * The roles of this user.
     */
    public val roles: List<String>?
        get() = null

}
