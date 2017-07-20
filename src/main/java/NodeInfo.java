import java.io.Serializable;

/**
 *Contains info about the nodes of the network such as the node ip,port and id.
 */
class NodeInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    String ipaddress;
    String port;
    int nodeID;

    /**
     * Parametrized constructor
     *
     * @param ipaddress IP Address of the Chord Node instance
     * @param port      The port/unique identifier of the Chord Node instance running on a system. This value differentiates multiple instance running on same system.
     * @param nodeID    The unique identifier of the Chord Node
     */
    NodeInfo(String ipaddress, String port, int nodeID) {
        this.ipaddress = ipaddress;
        this.port = port;
        this.nodeID = nodeID;
    }
}
