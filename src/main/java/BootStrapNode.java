import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The interface
 */
public interface BootStrapNode extends Remote {

    /**
     * This function will be called by any Chord Node that wishes to join the network. The main objective of this
     * function is to assign a unique identifier to a new Chord Node and add it the the ring. Other than this it also
     * implements the two different methods on how to be assigned identifiers based on network proximity.
     *
     * @param ipaddress The IP Address of the Chord Node joining the ring
     * @param port      The port/identifier of Chord Node instance joining the ring. This differentiates multiple
     *                  instance running on same system
     * @return ArrayList The list with metrics used to test the protocol.
     * @throws RemoteException Due to RMI.
     */
    ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port) throws RemoteException;

    /**
     * This function is called when a Chord Node leaves the ring or is found to be dead.
     *
     * @param n NodeInfo object storing details of the Chord Node instance
     * @throws RemoteException Due to RMI.
     */
    void removeNodeFromRing(NodeInfo n) throws RemoteException;

    /**
     * This function is called when a Chord Node has successfully joined the Chord ring
     *
     * @param nodeID The node identifier of the Chord Node that joined successfully
     * @throws RemoteException Due to RMI.
     */
    void acknowledgeNodeJoin(int nodeID) throws RemoteException;

    /**
     * This function displays the details of the Chord Nodes in the ring
     *
     * @throws RemoteException Due to RMI.
     */
    void displayNodesInRing() throws RemoteException;

    /**
     * This function returns the number of nodes currently in the ring
     *
     * @return int Number of nodes in the ring
     * @throws RemoteException Due to RMI.
     */
    int getNodesInRing() throws RemoteException;

    /**
     * The function generates a unique identifier for the Chord Node instance using SHA-1 algorithm
     *
     * @param key      The key with which the unique identifier is to be generated using SHA-1
     * @param maxNodes The maximum number of nodes in the ring
     * @return int Unique identifier for the Chord Node
     * @throws RemoteException          Due to RMI.
     * @throws NoSuchAlgorithmException Due to SHA-1 usage.
     */
    int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;

    /**
     * This function checks if a particular zone of the Chord ring is full ,i.e. all the slots are occupied by CHord Node instances
     *
     * @param zoneID The zone number in the Chord ring. Allowed range is 0 to (m-1).
     * @return is_filled Boolean value indicating is zone is full or not. True indicates full.
     * @throws RemoteException Due to RMI.
     */
    boolean isZoneFilled(int zoneID) throws RemoteException;

    /**
     * This function finds the new successor node if the current successor node is found to be dead or has crashed.
     *
     * @param n         NodeInfo object storing details of the Chord Node instance whose successor is to be found
     * @param dead_node NodeInfo object of the Chord Node instance which is dead
     * @return succ NodeInfo object of the new successor Chord Node
     * @throws RemoteException Due to RMI.
     */
    NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException;
}
