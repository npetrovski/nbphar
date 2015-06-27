package name.npetrovski.jphar;


public class PharException extends Exception {

    private static final long serialVersionUID = -7590586356994337876L;

    public PharException() {
        super();
    }

    public PharException(final String message) {
        super(message);
    }

    public PharException(final Throwable cause) {
        super(cause);
    }

    public PharException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
