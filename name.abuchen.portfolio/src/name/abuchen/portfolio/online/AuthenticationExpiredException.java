package name.abuchen.portfolio.online;

/**
 * Exception thrown by the quote feed provider to indicate that the
 * authentication has expired, e.g., the access token cannot be refreshed and a
 * new user login might be required.
 */
public class AuthenticationExpiredException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AuthenticationExpiredException()
    {
        super();
    }
}
