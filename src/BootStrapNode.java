import java.rmi.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

//Interface for the BootStrap Server node
public interface BootStrapNode extends Remote{
	
	public ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port, int zoneID) throws RemoteException;
	public void removeNodeFromRing(NodeInfo n) throws RemoteException;
	public void acknowledgeNodeJoin(int nodeID) throws RemoteException;
	public void displayNodesInRing() throws RemoteException;
	public int getNodesInRing() throws RemoteException;
	public ArrayList<Integer> getNodeIds() throws RemoteException;
	public int getMaxNodesInRing() throws RemoteException;
	public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;
	public boolean isZoneFilled(int zoneID) throws RemoteException;
	public NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException;
}
