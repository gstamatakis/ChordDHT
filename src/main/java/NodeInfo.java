import java.io.Serializable;

public class NodeInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    String ipaddress;
    public String port;
    int nodeID;

    /**
     * Parametrized constructor
     *
     * @param ipaddress IP Address of the Chord Node instance
     * @param port      The port/unique identifier of the Chord Node instance running on a system. This value differentiates multiple instance running on same system.
     * @param nodeID    The unique identifier of the Chord Node
     */
    public NodeInfo(String ipaddress, String port, int nodeID) {
        this.ipaddress = ipaddress;
        this.port = port;
        this.nodeID = nodeID;
    }
}
