import java.io.Serializable;

public class NodeInfo implements Serializable{
	
	private static final long serialVersionUID = 1L;
	public String ipaddress;//IP Address of the Chord Node instance
	public String port;//The port/unique identifier of the Chord Node instance running on a system.This value differentiates multiple instance running on same system.
	public int nodeID;//The unique identifier of the Chord Node
	
	/** 
	 * Parametrized constructor
	 * @param ipaddress IP Address of the Chord Node instance
	 * @param port The port/unique identifier of the Chord Node instance running on a system. This value differentiates multiple instance running on same system.
	 * @param nodeID The unique identifier of the Chord Node
	 */
	public NodeInfo(String ipaddress, String port, int nodeID) {
		this.ipaddress = ipaddress;
		this.port = port;
		this.nodeID = nodeID;
	}
	
	//Setters and Getters for data members
	public String getIpaddress() {
		return ipaddress;
	}
	public void setIpaddress(String ipaddress) {
		this.ipaddress = ipaddress;
	}
	public String getPort() {
		return port;
	}
	public void setPort(String port) {
		this.port = port;
	}
	public int getNodeID() {
		return nodeID;
	}
	public void setNodeID(int nodeID) {
		this.nodeID = nodeID;
	}	
}
