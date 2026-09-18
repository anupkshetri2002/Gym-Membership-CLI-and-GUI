package gym.exception;

/** Thrown when a lookup by ID finds no matching member. */
public class MemberNotFoundException extends GymSystemException {

    private static final long serialVersionUID = 1L;

    public MemberNotFoundException(int id) {
        super("No member exists with ID " + id + ".");
    }

    public MemberNotFoundException(String message) {
        super(message);
    }
}
