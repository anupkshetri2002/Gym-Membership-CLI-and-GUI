package gym.ui;

/**
 * Raised when the input stream ends (Ctrl-D, or a piped script running out of
 * lines). Unchecked because it is a "stop everything" signal rather than a
 * recoverable business error -- ConsoleApp catches it once, at the top of the
 * menu loop, and shuts down cleanly instead of looping forever on null input.
 */
public class InputExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InputExhaustedException() {
        super("Input stream ended.");
    }
}
