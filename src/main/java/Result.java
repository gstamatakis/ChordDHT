import java.io.Serializable;

/**
 * Represents the response received by various operations in the chord network.
 * Stores the latency for any operation in milliseconds and the communication messages exchanged for an operation.
 */
public class Result implements Serializable {
    long latency;
    int hopCount;

    /**
     * Default constructor.
     */
    Result() {
        this.hopCount = 0;
        this.latency = 0;
    }
}