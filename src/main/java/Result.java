import java.io.Serializable;

public class Result implements Serializable {
    public long latency; //Stores the latency for any operation in milliseconds.
    int hopCount; //Stores number of communication messages exchanged for an operation

    public Result() {
        this.hopCount = 0;
        this.latency = 0;
    }
}