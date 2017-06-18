import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

//Interface for the BootStrap Server node
public interface BootStrapNode extends Remote {

    ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port, int zoneID) throws RemoteException;

    void removeNodeFromRing(NodeInfo n) throws RemoteException;

    void acknowledgeNodeJoin(int nodeID) throws RemoteException;

    void displayNodesInRing() throws RemoteException;

    int getNodesInRing() throws RemoteException;

    ArrayList<Integer> getNodeIds() throws RemoteException;

    int getMaxNodesInRing() throws RemoteException;

    int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;

    boolean isZoneFilled(int zoneID) throws RemoteException;

    NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException;
}
