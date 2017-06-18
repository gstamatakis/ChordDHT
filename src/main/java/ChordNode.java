import java.rmi.*;
import java.security.NoSuchAlgorithmException;

//Interface for each Chord Node in the Chord Ring
public interface ChordNode extends Remote{
	// Routing
	public NodeInfo find_successor(int id, Result result) throws RemoteException;
	public NodeInfo find_predecessor(int id, Result result) throws RemoteException;
	public NodeInfo closest_preceding_finger(int id) throws RemoteException;
	
	// Node joins and departures
	public void init_finger_table(NodeInfo n, Result result) throws RemoteException;
	//public void update_others_after_join(Result result) throws RemoteException;
	//public void update_finger_table_join(NodeInfo s, int i, Result result) throws RemoteException;
	public void update_finger_table_leave(NodeInfo t, int i, NodeInfo s, Result result) throws RemoteException;
	public void update_others_before_leave(Result result) throws RemoteException;
	public void migrate_keys(NodeInfo pred, NodeInfo newNode, Result result) throws RemoteException;
	
	// periodic stabilization
	public void stabilize(Result result) throws RemoteException;
	public void notify_successor(NodeInfo e) throws RemoteException;
	public void fix_fingers(Result result) throws RemoteException;
	public void send_beat() throws RemoteException;
	
	// access control for member variables
	public void set_predecessor(NodeInfo p) throws RemoteException;
	public NodeInfo get_successor() throws RemoteException;
	public NodeInfo get_predecessor() throws RemoteException;
	public void set_successor(NodeInfo n) throws RemoteException;
	
	// generate a hashed ID for given key
	public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;
	
	// CLI
	public void print_finger_table() throws RemoteException;
	public boolean insert_key(String key, String value, Result result) throws RemoteException;
	public boolean delete_key(String key, Result result) throws RemoteException;
	public String get_value(String key, Result result) throws RemoteException;
	public boolean leave_ring(Result result) throws RemoteException;
	public void display_data_stored() throws RemoteException;
	
	// To manipulate local data structures
	public boolean insert_key_local(int keyID, String key, String value, Result result) throws RemoteException;
	public boolean delete_key_local(int keyID, String key, Result result) throws RemoteException;
	public String get_key_local(int keyID, String key, Result result) throws RemoteException;

	// Dummy call for measuring latency
	public void makeCall(NodeInfo n) throws RemoteException;

	// For metric collections
	public long get_insert_latency() throws RemoteException;
	public long get_query_latency() throws RemoteException;
	public long get_join_time() throws RemoteException;
	public int get_insert_hopcount() throws RemoteException;
	public int get_query_hopcount() throws RemoteException;
	public int get_join_hopcount() throws RemoteException;
}