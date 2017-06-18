import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

//Interface for each Chord Node in the Chord Ring
public interface ChordNode extends Remote {
    // Routing
    NodeInfo find_successor(int id, Result result) throws RemoteException;

    NodeInfo find_predecessor(int id, Result result) throws RemoteException;

    NodeInfo closest_preceding_finger(int id) throws RemoteException;

    // Node joins and departures
    void init_finger_table(NodeInfo n, Result result) throws RemoteException;

    //void update_others_after_join(Result result) throws RemoteException;
    //void update_finger_table_join(NodeInfo s, int i, Result result) throws RemoteException;
    void update_finger_table_leave(NodeInfo t, int i, NodeInfo s, Result result) throws RemoteException;

    void update_others_before_leave(Result result) throws RemoteException;

    void migrate_keys(NodeInfo pred, NodeInfo newNode, Result result) throws RemoteException;

    // periodic stabilization
    void stabilize(Result result) throws RemoteException;

    void notify_successor(NodeInfo e) throws RemoteException;

    void fix_fingers(Result result) throws RemoteException;

    void send_beat() throws RemoteException;

    NodeInfo get_successor() throws RemoteException;

    void set_successor(NodeInfo n) throws RemoteException;

    NodeInfo get_predecessor() throws RemoteException;

    // access control for member variables
    void set_predecessor(NodeInfo p) throws RemoteException;

    // generate a hashed ID for given key
    int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException;

    // CLI
    void print_finger_table() throws RemoteException;

    boolean insert_key(String key, String value, Result result) throws RemoteException;

    boolean delete_key(String key, Result result) throws RemoteException;

    String get_value(String key, Result result) throws RemoteException;

    boolean leave_ring(Result result) throws RemoteException;

    void display_data_stored() throws RemoteException;

    // To manipulate local data structures
    boolean insert_key_local(int keyID, String key, String value, Result result) throws RemoteException;

    boolean delete_key_local(int keyID, String key, Result result) throws RemoteException;

    String get_key_local(int keyID, String key, Result result) throws RemoteException;

    // Dummy call for measuring latency
    void makeCall(NodeInfo n) throws RemoteException;

    // For metric collections
    long get_insert_latency() throws RemoteException;

    long get_query_latency() throws RemoteException;

    long get_join_time() throws RemoteException;

    int get_insert_hopcount() throws RemoteException;

    int get_query_hopcount() throws RemoteException;

    int get_join_hopcount() throws RemoteException;
}