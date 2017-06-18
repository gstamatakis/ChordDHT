/**
 * This class serves as the starting point for any Chord Node instance that will be spawned and 
 * has all the associated function to support various operations.
 */

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
//import java.text.SimpleDateFormat;

import org.apache.log4j.*;
public class ChordNodeImpl extends UnicastRemoteObject implements ChordNode{

	private static final long serialVersionUID = 1L;
	public static int num = 0;  // used during rmi registry binding
	
	// m and maxNodes in ChordNodeImpl should be same as
	// m and maxNodes in BootStrapNodeImpl.
	public static int m = 5; 
	public static int maxNodes = 32; // maxNodes = 2^m
	public static int ftsize = 2*m - 1; // finger table size
	
	public static BootStrapNode bootstrap;
	public static String bootstrapServer = null;
	public ChordNode cn;
	public FingerTableEntry[] fingertable = null; //Data Structure to store the finger table for the Chord Node
	public HashMap<Integer, HashMap<String, String>> data = new HashMap<Integer, HashMap<String, String>>();//Data store for each Chord Node instance
	public ReentrantReadWriteLock data_rwlock = new ReentrantReadWriteLock();
	
	public NodeInfo node;
	public NodeInfo predcessor;
	
	public static final int RUNSTABILIZEPERIOD = 5000; // 5 seconds
	public static final int RUNFIXFINGERPERIOD = 5000; // 5 seconds
	public static int fix_finger_count = 0; // store the id of next finger entry to update
	static Timer timerStabilize = new Timer();
	static Timer timerFixFinger = new Timer();
	public ArrayList<HashMap<String, Result>> metrics;
	public static long totalLatency_insert = 0; // total time for all insert operations 
	public static long totalLatency_query = 0; // total time for all query operations 
	public static long totalLatency_join = 0; // total time to join operation

	// Variables to assist in final result and metric collection for hop count
	public static int totalHopCount_insert = 0;
	public static int totalHopCount_query = 0;
	public static int totalHopCount_join = 0;
	
	private static Logger log = null;

	protected ChordNodeImpl(NodeInfo node) throws RemoteException {
		super();
		this.node = node;
		this.predcessor = null;
		this.fingertable = new FingerTableEntry[ftsize];
		this.metrics = new ArrayList<HashMap<String, Result>>();
	}

	public ChordNodeImpl() throws RemoteException{
		super();
		this.node = null;
		this.predcessor = null;
		this.fingertable = new FingerTableEntry[ftsize];
		this.metrics = new ArrayList<HashMap<String, Result>>();
	}

	@Override
	/** 
	 * This function is used to determine the successor node for a given node identifier
	 * @param id The node identifer whose successor is to be found
	 * @param result Result object to assist in metrics collection
	 * @return newNode NodeInfo object containing details of successor node
	 */
	public NodeInfo find_successor(int id, Result result) throws RemoteException {
		log.debug("In find_successor for id: " + id);
		NodeInfo newNode = null;
		
		newNode = find_predecessor(id, result);
		
		log.debug("Predecessor for id: " + id + " is " + newNode.nodeID);
		
		try {
			ChordNode c = (ChordNode)Naming.lookup("rmi://"+newNode.ipaddress+"/ChordNode_" + newNode.port);
			if(newNode.nodeID != this.node.nodeID)
				result.hopCount++;
			newNode = c.get_successor();
			log.debug("Successor for id: " + id + " is " + newNode.nodeID + "\n");
		} catch (Exception e) {
			log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			return null;
		} 
		
		return newNode;
	}
	
	@Override
	/**
	 * This function is used to find the predecessor node for a given node identifier
	 * @param id The node identifier whose predecessor is to be found
	 * @param result Result object to assist in metrics collection
	 * @return nn NodeInfo object containing details of predecessor node
	 */
	public NodeInfo find_predecessor(int id, Result result) throws RemoteException {
		NodeInfo nn = this.node;
		int myID = this.node.nodeID;
		int succID = this.get_successor().nodeID;
		ChordNode c = null;
		
		// loop till id is not in the circular range (nn, successor(nn))
		// at each iteration, update nn = nn.closest_preceding_finger(id)
		// when loop ends, nn will be id's predecessor
		while((myID >= succID && (myID >= id && succID < id)) || (myID < succID && (myID >= id || succID < id))){
			try {
				log.debug("Looking for closest preceding finger of id: " + id + " in node: " + nn.nodeID);
				if(nn == this.node){
					nn = closest_preceding_finger(id);
				}else{
					if(nn.nodeID != this.node.nodeID)
						result.hopCount++;
					nn = c.closest_preceding_finger(id);						
				}
				
				myID = nn.nodeID;
				c = (ChordNode)Naming.lookup("rmi://"+nn.ipaddress+"/ChordNode_" + nn.port);
				succID = c.get_successor().nodeID;
				if(nn.nodeID != this.node.nodeID)
					result.hopCount++;
				
			} catch (Exception e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
				return null;
			} 
		}
		
		return nn;
	}

	@Override
	/**
	 * This function is used to determine the closest preceding finger(CPF) of the given node identifier
	 * @param id Node identifier whose CPF is to be found.
	 * @return NodeInfo NodeInfo object containing details of the CPF node
	 */
	public NodeInfo closest_preceding_finger(int id) {
		int myID = node.nodeID;
		int midID = fingertable[m-1].successor.nodeID;
	
		for(int i = ftsize - 1; i >= 0; i--){
			int succID = fingertable[i].successor.nodeID;
			if((myID < id && (succID > myID && succID < id)) || (myID >= id && (succID > myID || succID < id))){
				return fingertable[i].successor;
			}		
		}
			
		return this.node;
	}

	/**
	 * This function is called immediately after the join to initialize data structures specifically the finger table entries to assist in routing
	 * @param n The successor node of the current Chord Node instance
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void init_finger_table(NodeInfo n, Result result) throws RemoteException {
		ChordNode c = null;
		
		try {	
			c = (ChordNode)Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
			
			// initialize finger table
			int myID = this.node.nodeID;
			for(int i = 0; i < ftsize-1; i++){
				int nextID = fingertable[i].successor.nodeID;
				
				// check if the (i+1)th finger table entry should be same as ith entry
				if((myID >= nextID && (fingertable[i+1].start >= myID || fingertable[i+1].start <= nextID)) || 
						(myID < nextID && (fingertable[i+1].start >= myID && fingertable[i+1].start <= nextID))){
							
					fingertable[i+1].successor = fingertable[i].successor;		
					
				}else{ // else invoke find_successor
				
					if(n.nodeID != this.node.nodeID)
						result.hopCount++;
					
					NodeInfo s = c.find_successor(fingertable[i+1].start, result);
					
					// check if FTE needs to be updated to s, or it should continue pointing to me
					int myStart = fingertable[i+1].start;
					int succ = s.nodeID;
					int mySucc = fingertable[i+1].successor.nodeID;
					
					//System.out.println("myStart\tsucc\tmySucc");
					//System.out.println(myStart + "\t" + succ + "\t" + mySucc);
					
					if(myStart > succ){
						succ += maxNodes;
					}
					if(myStart > mySucc){
						mySucc += maxNodes;
					}
					if(myStart <= succ && succ <= mySucc){
						fingertable[i+1].successor = s;
					}
				}
				log.debug("FTE " + (i+1) + " set as: " + fingertable[i+1].start + " || " + fingertable[i+1].successor.nodeID);
			}
			
			// set predecessor of successor as me
			if(n.nodeID != this.node.nodeID){
				result.hopCount++;
			}
			c.set_predecessor(this.node);
			log.info("predecessor of node " + n.nodeID + " set as " + this.node.nodeID);
			
		} catch (MalformedURLException | NotBoundException e) {
			log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			
			// If finger table initialization couldn't complete.
			// Stabilize and fix fingers will take care of it eventually.		
		}
		
	}

	
	// No need for updating other nodes after joining now.
	// This is taken care of by periodic fix_fingers routine.
	
	/*
	@Override
	public void update_others_after_join(Result result) {
		//System.out.println("updating finger table of other nodes.");
		for(int i = 1; i <= ftsize; i++){
			int id = this.node.nodeID - (int)Math.pow(2, i-1) + 1;
			if(id < 0){
				id += maxNodes;
			}
			
			NodeInfo p = find_predecessor(id, result);
			//System.out.println("Predecessor of id: " + id + " is " + p.nodeID);
			//System.out.println("iteration " + i + ": try to update Finger table entry " + (i-1) + " of node " + p.nodeID + " with my nodeID.");
			
			try {
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+p.ipaddress+"/ChordNode_" + p.port);
				if(this.node.nodeID != p.nodeID)
					result.hopCount++;
				c.update_finger_table_join(this.node, i-1, result);				
			} catch (Exception e) {
				//System.out.println("Chord Node lookup in update_others");
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			}
		}
	}

	@Override
	public void update_finger_table_join(NodeInfo s, int i, Result result) {
		//System.out.println("Trying to update Finger table entry " + i + " to " + s.nodeID);
		
		int myID = this.node.nodeID;
		int nextID = fingertable[i].successor.nodeID;
		
		if(((myID >= nextID && (s.nodeID >= myID || s.nodeID < nextID)) || 
				(myID < nextID && (s.nodeID >= myID && s.nodeID < nextID))) && myID != s.nodeID){
			fingertable[i].successor = s;
			//System.out.println("Finger table entry " + i + " successfully set as " + s.nodeID);
			NodeInfo p = predcessor;
			try {
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+p.ipaddress+"/ChordNode_" + p.port);
				if(this.node.nodeID != p.nodeID)
					result.hopCount++;
				c.update_finger_table_join(s, i, result);				
			} catch (Exception e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			} 
		}
	}
	*/

	/**
	 * This function is used to update other nodes when a Chord Node voluntarily leaves the ring/network
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void update_others_before_leave(Result result) throws RemoteException{
		log.info("updating finger table of other nodes before leaving");
		
		for(int i = 1; i <= ftsize; i++){
			int id = this.node.nodeID - (int)Math.pow(2, i-1) + 1;
			if(id < 0){
				id += maxNodes;
			}
			
			NodeInfo p = find_predecessor(id, result);
			log.debug("Predecessor of id: " + id + " is " + p.nodeID);
			log.debug("iteration " + i + ": try to update Finger table entry " + (i-1) + " of node " + p.nodeID + " with my successor.");
			
			try {
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+p.ipaddress+"/ChordNode_" + p.port);
				c.update_finger_table_leave(this.node, i-1, this.get_successor(), result);
				if(this.node.nodeID != p.nodeID)
					result.hopCount++;
			} catch (Exception e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			}
		}
	}

	/**
	 * This function is used to update the finger table entries of Chord nodes when a node leaves the network/ring
	 * @param t The NodeInfo object of the node which departs from the ring
	 * @param i The ith finger table entry to be updated
	 * @param s The NodeInfo object to be set as successor in the finger table entry
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void update_finger_table_leave(NodeInfo t, int i, NodeInfo s, Result result) throws RemoteException{
		if(fingertable[i].successor.nodeID == t.nodeID && t.nodeID != s.nodeID){
			fingertable[i].successor = s;
			log.debug("Node " + t.nodeID + " departing. Finger table entry " + i + " successfully moditfied to " + s.nodeID);
			NodeInfo p = predcessor;
			
			try {
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+p.ipaddress+"/ChordNode_" + p.port);
				c.update_finger_table_leave(t, i, s, result);
				if(this.node.nodeID != p.nodeID)
					result.hopCount++;
			} catch (Exception e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			} 
		}
	}

	@Override
	/**
	 * Dummy Function to send a heart beat message to verify status
	 */
	public void send_beat() throws RemoteException{		
		log.debug("Acknowledged heart beat message.");
		return;
	}
	
	/*
	(1) Check if successor is dead (try pinging/reaching it twice).
	(2) Find the next finger table entry that does not point either to me or to the dead successor.
	(3) Once such a finger table entry is found, query that node for its predecessor.
	(3a) In a loop, follow the predecessor chain till you find the node whose predcessor is our dead succesor.
	(3b) Set my succesor as that node and set the predessor of that node as me.
	(3c) Inform bootstrap to update its list of active chord nodes.
	(4) If no such finger table entry is found, contact bootstrap to return a different successor.
	*/
	
	@Override
	/**
	 * The stabilize function is used to periodically verify the current nodes immediate successor and tell the successor about itself
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void stabilize(Result result) {
		log.debug("Stabilization running on chord Node" +this.node.nodeID);
		
		NodeInfo successorNodeInfo = null, tempNodeInfo = null;
		ChordNode successor = null, temp = null;
		
		try{
			successorNodeInfo = get_successor();
			
			if(successorNodeInfo.nodeID==this.node.nodeID){
				// single node so no stabilization
				successor = this;
			}else{
				log.debug("RMI CALL TO HEART BEAT:"+"rmi://"+successorNodeInfo.ipaddress+"/ChordNode_" + successorNodeInfo.port);
				successor = (ChordNode) Naming.lookup("rmi://"+successorNodeInfo.ipaddress+"/ChordNode_" + successorNodeInfo.port);
				successor.send_beat();
			}
		}catch(Exception e){
			successor = null;
			log.error("Failed Heart beat message. Error in stabilize: "+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			
			// Second attempt for heart beat
			try {
				successor = (ChordNode) Naming.lookup("rmi://"+successorNodeInfo.ipaddress+"/ChordNode_" + successorNodeInfo.port);
				successor.send_beat();
			} catch (Exception e1) {
				successor = null;
				log.error("Failed Heart beat message. Error in stabilize: "+e1.getClass() + ": " +  e1.getMessage() + ": " + e1.getCause() + "\n" +  e1.getStackTrace().toString(),e1);
			}
		}
		
		// Current successor is dead. Get a new one.
		if(successor == null){
			log.error("Failed to contact succesor. Declare succesor dead");
			
			// iterate over fingertable entries till you find a node that is not me and not the dead succesor 
			int i;
			for (i = 1; i < ftsize ; i++) {
				tempNodeInfo = fingertable[i].successor;	
				if(tempNodeInfo.nodeID != successorNodeInfo.nodeID && tempNodeInfo.nodeID != node.nodeID)
					break;
			}
			
			if(i != ftsize) {
				log.debug("Following predecessor chain starting from node " + tempNodeInfo.nodeID);
				 
				// follow the predecessor chain from tempNodeInfo
				while(true){
					try {
						log.debug("Current node in predecessor chain " + tempNodeInfo.nodeID);
						temp = (ChordNode) Naming.lookup("rmi://"+tempNodeInfo.ipaddress+"/ChordNode_" + tempNodeInfo.port);
						if(temp.get_predecessor().nodeID == successorNodeInfo.nodeID){
							temp.set_predecessor(this.node);
							this.set_successor(tempNodeInfo);
							log.debug("New succesor is " + tempNodeInfo.nodeID);
							break;
						}
						tempNodeInfo = temp.get_predecessor();
					} catch (Exception e) {
						log.error("Error in stabilize while following predecessor chain: "+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);						
						break;
					}
				}
				
				try{
					//notify the bootstrap of node exit
					bootstrap.removeNodeFromRing(successorNodeInfo);

				} catch (RemoteException e) {
					log.error("Error in notifying bootstrap about dead node: "+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" + e.getStackTrace().toString(),e);
					return;
				}
				
			} else {
				// My finger table does not include a different node.
				// Ask bootstrap for a new successor.
				try{
					NodeInfo new_suc = bootstrap.findNewSuccessor(this.node, successorNodeInfo);
					this.set_successor(new_suc);
					temp = (ChordNode) Naming.lookup("rmi://" + new_suc.ipaddress+"/ChordNode_" + new_suc.port);
					temp.set_predecessor(this.node);
				} catch (Exception e) {
					log.error("Error in requesting new succesor from bootstrap: "+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" + e.getStackTrace().toString(),e);
					return;
				}
			}			
		}
		
		// Current successor is alive. Ensure that you are your successor's predecessor.
		else {
			NodeInfo x = null;
			try {
				x = successor.get_predecessor();
				if(this.node.nodeID != successorNodeInfo.nodeID)
						result.hopCount++;
			} catch (RemoteException e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			}
			
			if((x != null)&&(inCircularInterval(x.nodeID, this.node.nodeID, this.fingertable[0].successor.nodeID)))
				this.fingertable[0].successor = x;
			
			try {
				// Ensure that you are your successor's predecessor is set correctly.
				if(successorNodeInfo.nodeID==this.node.nodeID)
					successor.notify_successor(this.node); 
			} catch (RemoteException e) {
				log.error("Error in calling the successor's notifyall"+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			}
		}

	}

	@Override
	/**
	 * This function notifes the other nodes that it might be their predecessor
	 * @param n NodeInfo object of the probable successor node to notify
	 * @return null
	 */
	public void notify_successor(NodeInfo n) {
		if(this.predcessor == null)
			this.predcessor = n;
		if(inCircularInterval(n.nodeID, this.predcessor.nodeID, this.node.nodeID))
			this.predcessor = n;
			
	}
	
	/**
	 * This function checks if a node identifer is in the circular interval of two other nodes
	 * @param x The node identifier which is to verified 
	 * @param a The start node in the circular interval
	 * @param b The end node in the circular interval
	 * @return val true if node is in circular interval, else false
	 */
	public boolean inCircularInterval(int x, int a, int b){
		boolean val = false;
		if(a == b)
			val = true;
		else if(a < b){
			// normal range
			if((x > a) && (x < b))
				val = true;
		} else{
			// when on one current node is after 0 but predecessor is before 0
			if((x > a) && (x < (b + maxNodes))){
				// in ring before 0
				val = true;
			} else if((x < b) && ((x + maxNodes) > a)){
				// in ring after 0
				val = true;
			}
		}
		return val;
	}
	
	public boolean inCircularIntervalStartInclude(int x, int a, int b){
		return (x == a) ? true : inCircularInterval(x, a, b);
	}
	
	public boolean inCircularIntervalEndInclude(int x, int a, int b){
		return (x == b) ? true : inCircularInterval(x, a, b);
	}

	@Override
	/**
	 * This function is used to periodically refresh finger table entries
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void fix_fingers(Result result) throws RemoteException {
		log.debug("Fix_fingers running on chord Node "+ node.nodeID);
		
		//periodically fix all fingers		
		fix_finger_count = fix_finger_count+1;
		if(fix_finger_count == ftsize)
			fix_finger_count = 1;
		log.debug("Running fix_finger with i: "+fix_finger_count);
		fingertable[fix_finger_count].successor = find_successor(fingertable[fix_finger_count].start,result);
	}
	
	/**
	 * Starting point for the Chord Node instances
	 * @param args variable length command line argument list
	 * @return null
	 */
	public static void main(String []args) throws RemoteException{
		ChordNode c = null;
		ChordNodeImpl cni = null;
		boolean running = true;
		long startTime, endTime, timetaken;
		Result result = new Result();
		HashMap<String, Result> met = null;
		
		if(args.length < 3){
			System.out.println("Usage : java ChordNodeImpl <ip address of current node> <ipaddress of bootstrap> <zone-ID(range 0-m)>");
			return;
		}
		
		// Logging Module initialize
		PatternLayout layout = new PatternLayout();
        String conversionPattern = "%-7p %d [%t] %c %x - %m%n";
        layout.setConversionPattern(conversionPattern);
        
        // creates file appender
        FileAppender fileAppender = new FileAppender();
		//String dt = new SimpleDateFormat("MM-dd-yyy hh-mm-ss").format(new Date());
        //fileAppender.setFile("logs/chord_" + dt + ".log");
		fileAppender.setFile("logs/chord.log");
        fileAppender.setLayout(layout);
        fileAppender.activateOptions();
        
        //logger assign
        log = Logger.getLogger(ChordNodeImpl.class);
        log.addAppender(fileAppender);
        log.setLevel(Level.DEBUG);
        
        log.info("\n## Creating chord node instance ##\n");
		
		String nodeIPAddress = args[0];
		int zoneID = Integer.parseInt(args[2]);
		
		try {
			startTime = System.currentTimeMillis();
			result.latency = startTime;
			String rmiUrl = "rmi://" + args[1] + "/ChordRing";
			log.debug("Contacting Bootstrap Server "+rmiUrl);
			bootstrap = (BootStrapNode) Naming.lookup(rmiUrl);
		} catch (MalformedURLException | RemoteException | NotBoundException e) {
			log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
		}
		
		try{
			while(true){
				log.trace("Checking for existing chord node instances [ChordNode_" + num + "] running on localhost");
				try{
					c = (ChordNode) Naming.lookup("rmi://localhost/ChordNode_" + num);
				} catch(NotBoundException e){
					c = null;
				}
				if(c == null){
					cni = new ChordNodeImpl();
					Naming.rebind("ChordNode_" + num, cni);
					break;
				}else{
					num++;
				}
			}			
		}catch(Exception e){
			log.error("Error in binding ChordNode"+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(), e);
			return;
		}
		
		log.info("Chord node instance created with rmi name [ChordNode_" + num + "]");
		
		ArrayList<NodeInfo> nodes = bootstrap.addNodeToRing(nodeIPAddress, num + "", zoneID);
		if(nodes != null){
			cni.node = nodes.get(0);
			FingerTableEntry fte = new FingerTableEntry((cni.node.nodeID + 1)%maxNodes, nodes.get(1));
			cni.fingertable[0] = fte;
			cni.predcessor = nodes.get(2);
			log.info("My ID: " + cni.node.nodeID);
			log.info("Successor ID - " + cni.fingertable[0].successor.nodeID);
			log.info("Predcessor ID - " + cni.predcessor.nodeID);
		}else{
			log.error("Join unsuccessful");
			return;
		}	
		
		fileAppender.close();
		fileAppender = new FileAppender();
		fileAppender.setFile("logs/chord_" + cni.node.nodeID +".log");
		fileAppender.setLayout(layout);
		fileAppender.setAppend(false);
        fileAppender.activateOptions();
		log.removeAllAppenders();
        log.addAppender(fileAppender);
		
		cni.run(result);
		
		Scanner sc = new Scanner(System.in);
		String key, value;
		boolean res;
		int choice;
		
		while(running){
			System.out.println("\nMenu: \n1. Print Finger Table"
					+ "\n2. Get Key \n3. Put Key \n4. Delete Key \n5. Display data stored \n9. Leave Chord Ring");
			System.out.println("Enter your choice: ");
			try {
				choice  = sc.nextInt();
			} catch(Exception e) {
				System.out.println("Give valid input please.");
				continue;
			} finally {
				sc.nextLine();  // Consume newline left-over
				System.out.println("\n");
			}
			
			switch(choice){
			case 1:
				cni.print_finger_table();
				break;
			case 2:
				System.out.print("Enter key: ");
				key = sc.nextLine();
				Result getHops = new Result();
				startTime = System.currentTimeMillis();
				value = cni.get_value(key, getHops);
				if(value != null)
					System.out.println("Value is: " + value);
				else 
					System.out.println("Key not found.");
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				getHops.latency = timetaken;
				log.info("Hop Count for get key operation: " + getHops.hopCount);
				log.info("Time taken for get key operation: " + timetaken + "ms");
				met = new HashMap<String, Result>();
				met.put("GET", getHops);
				cni.metrics.add(met);
				break;
			case 3:
				System.out.print("Enter key: ");
				key = sc.nextLine();
				System.out.print("Enter value: ");
				value = sc.nextLine();
				Result insHops = new Result();
				startTime = System.currentTimeMillis();
				res = cni.insert_key(key, value, insHops);
				if(res == true)
					System.out.println(key + ": " + value + " successfully inserted.");
				else 
					System.out.println("Insertion unsuccessful.");
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				insHops.latency = timetaken;
				log.info("Hop Count for insert key operation: " + insHops.hopCount);
				log.info("Time taken for insert key operation: " + timetaken + "ms");
				met = new HashMap<String, Result>();
				met.put("INSERT", insHops);
				cni.metrics.add(met);
				break;
			case 4:
				System.out.print("Enter key: ");
				key = sc.nextLine();
				Result delHops = new Result();
				startTime = System.currentTimeMillis();
				res = cni.delete_key(key, delHops);
				if(res == true)
					System.out.println(key + " successfully deleted.");
				else 
					System.out.println("Key not found. Deletion unsuccessful.");
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				delHops.latency = timetaken;
				log.info("Hop Count for delete key operation: " + delHops.hopCount);
				log.info("Time taken for delete key operation: " + timetaken + "ms");
				met = new HashMap<String, Result>();
				met.put("DELETE", delHops);
				cni.metrics.add(met);
				break;
			case 5:
				System.out.println("Printing all data stored in the node");
				cni.display_data_stored();
				break;
			case 9:
				Result lhops = new Result();
				startTime = System.currentTimeMillis();
				if(cni.leave_ring(lhops) == true) {
					timerStabilize.cancel();
					timerFixFinger.cancel();
					timerStabilize.purge();
					timerFixFinger.purge();	
					System.out.println("Node left...No more operations allowed");
					
					try {
						log.info("Removing the node from ring [ChordNode_" + cni.node.port + "]");
						Naming.unbind("rmi://localhost/ChordNode_" + cni.node.port);
						log.debug("ChordNode RMI object unbinded");
					} catch (Exception e) {
						log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
					}
					running = false;
					endTime = System.currentTimeMillis();
					timetaken = endTime - startTime;
					lhops.latency = timetaken;
					log.info("Hop Count while leaving Chord network: " + lhops.hopCount);	
					log.info("Time taken for leaving the Chord network:" + timetaken + "ms");
					met = new HashMap<String, Result>();
					met.put("LEAVE", lhops);
					cni.metrics.add(met);
					sc.close();				
				} else {
					System.out.println("Error: Cannot leave ring right now");
				}
				break;
			}
		}		
		
	}

	/** 
	 * This function is called after contacting the BootStrap server and obtaining the successor and predecessor nodes to initialize finger table and update other nodes after joining.
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */
	public void run(Result result) {
		ChordNode c;
		NodeInfo suc = fingertable[0].successor;
		
		int ringsize = -1;
		long endTime;
		try {
			ringsize = bootstrap.getNodesInRing();
		} catch (RemoteException e) {
			log.error(e);
		}
		
		// Allocate storage for finger table 
		int i, j;
		for(i = 1; i < m; i++){
			int start = (this.node.nodeID + (int) Math.pow(2,i)) % maxNodes;
			NodeInfo succ = this.node;
			FingerTableEntry fte = new FingerTableEntry(start, succ);
			fingertable[i]=fte;
		}
		for(j = m-2; i < ftsize; i++, j--){
			int start = (this.node.nodeID + maxNodes - (int) Math.pow(2,j)) % maxNodes;
			NodeInfo succ = this.node;
			FingerTableEntry fte = new FingerTableEntry(start, succ);
			fingertable[i]=fte;
		}
		
		/* Order of operations for initializing a new node:
		1) Initialize Finger table.
		2) Inform successor node about new node.
		3) Migrate keys from successor to new node.
		4) Inform predecessor node about new node.
		P.S. Only after operation (4) is the new node actually connected to the ring.
		Once predecessor knows about the new node, other nodes in the ring
		will eventually learn of it through fix fingers.
		
		Note: Don't change the order.
		This order ensures that the new node does not receive any key-related requests
		before it joins the ring.
		Also, any key requests for keys belonging to the new node,
		that arrived at the successor node while migration was in progress,
		will eventually be routed to the new node.
		*/
		
		//System.out.println("Ring Size: " + ringsize);
		if(ringsize > 1){ //More than one node in the Chord Ring

			log.info("Starting finger table initialization.\n");
			try {
				this.init_finger_table(suc, result);
			} catch (RemoteException e) {
				log.error(e);
			}
			
			log.info("Starting key migration");
			try {
				c = (ChordNode)Naming.lookup("rmi://" + suc.ipaddress + "/ChordNode_" + suc.port);
				c.migrate_keys(this.predcessor, this.node, result);
				if(this.node.nodeID != suc.nodeID)
					result.hopCount++;				
			} catch (Exception e) {
				log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			}
			
			try {
				// set successor of predecessor as me
				c = (ChordNode)Naming.lookup("rmi://" + predcessor.ipaddress+"/ChordNode_" + predcessor.port);
				c.set_successor(this.node);
				log.info("successor of node " + predcessor.nodeID + " set as " + this.node.nodeID);
				
				if(predcessor.nodeID != this.node.nodeID)
					result.hopCount++;
				
			} catch (Exception e) {
					log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
					// Failed to update predcessor, stabilize will eventually handle
			}
		}
		
		log.info("Done with node initialization");
		
		try {
            bootstrap.acknowledgeNodeJoin(this.node.nodeID);
            endTime = System.currentTimeMillis();
			long timetaken = endTime - result.latency;
			result.latency = timetaken;
			log.info("Hop Count while joining Chord network: " + result.hopCount);
			log.info("Time taken for node to join the Chord network: " + timetaken + "ms");
			totalLatency_join = timetaken;
			totalHopCount_join = result.hopCount;
			HashMap<String, Result> m = new HashMap<String, Result>();
			m.put("JOIN", result);
			this.metrics.add(m);
        } catch (Exception e) {}
		
		
		//Set the timer to run notify every RUNSTABILIZEPERIOD
		timerStabilize.scheduleAtFixedRate(new TimerTask() {
		    public void run() {
		        stabilize(result);
		    }
		},new Date(System.currentTimeMillis()), ChordNodeImpl.RUNSTABILIZEPERIOD);
		
		//Set the timer to run notify every RUNFIXFINGERPERIOD
		timerFixFinger.scheduleAtFixedRate(new TimerTask() {
		    public void run() {
				try {
					fix_fingers(result);
				} catch (Exception e) {
					log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
				}
		    }
		},new Date(System.currentTimeMillis()), ChordNodeImpl.RUNFIXFINGERPERIOD);
	}
	
	public NodeInfo get_predecessor() throws RemoteException {
		return this.predcessor;
	}

	@Override
	public void set_predecessor(NodeInfo p) throws RemoteException {
		this.predcessor = p;		
	}

	@Override
	public NodeInfo get_successor() throws RemoteException {
		return this.fingertable[0].successor;		
	}
	
	@Override
	public void set_successor(NodeInfo n) throws RemoteException {
		this.fingertable[0].successor = n;		
	}
	
	public NodeInfo get_node_info() throws RemoteException {
		return this.node;
	}
	
	@Override
	/** 
	 * This function is used to print the finger table entries to verify if they are being set properly
	 * @param null
	 * @return null
	 */
	public void print_finger_table() throws RemoteException {
		System.out.println("My ID: " + node.nodeID + " Predecessor ID: " + (predcessor==null?"NULL":predcessor.nodeID));
		System.out.println("Index\tStart\tSuccessor ID\tIP Address\tRMI Identifier");
		for(int i = 0; i < ftsize; i++){
			System.out.println((i+1) + "\t" + fingertable[i].start + "\t" + fingertable[i].successor.nodeID + "\t\t" + fingertable[i].successor.ipaddress + "\t" + fingertable[i].successor.port);
		}		
	}
	
	@Override
	/**
	 * Wrapper function to insert a new key-value pair
	 * @param key The key for the data
	 * @param value The value associated to the key
	 * @param result result object to assist in metric collection
	 * @return boolean Indiator to check if operation was successful or not
	 */
	public boolean insert_key(String key, String value, Result result) {
		try {
			long endTime,startTime,timetaken;
			startTime = System.currentTimeMillis();
			int keyID = generate_ID(key, maxNodes);
			log.info("Inserting keyID " + keyID + " for key " + key + " with value " + value);
			NodeInfo n = find_successor(keyID, result);
			if(n != this.node){
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+n.ipaddress+"/ChordNode_" + n.port);
				result.hopCount++;
				boolean flag =  c.insert_key_local(keyID, key, value, result);	
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				totalLatency_insert +=timetaken;
				totalHopCount_insert += result.hopCount;
				result.latency = timetaken;
				log.info("Hop Count for insert key operation: " + result.hopCount);
				log.info("Time taken for insert key operation: " + timetaken + "ms");
				return flag;
			} else{
				boolean flag=  insert_key_local(keyID, key, value, result);
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				result.latency = timetaken;
				totalLatency_insert +=timetaken;
				totalHopCount_insert += result.hopCount;
				log.info("Hop Count for insert key operation: " + result.hopCount);
				log.info("Time taken for insert key operation: " + timetaken + "ms");
				return flag;
			}
		} catch (Exception e) {
			log.error("Error in inserting keys"+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(), e);
			return false;
		}
	}

	@Override
	/**
	 * Wrapper Function to delete data tagged to a key
	 * @param key Key to be deleted
	 * @param result Result object to assist in metric collection
	 * @return boolean Indiator to check if operation was successful or not
	 */
	public boolean delete_key(String key, Result result) {
		try {
			int keyID = generate_ID(key, maxNodes);
			log.info("Deleting key :"+key+"with key hash"+keyID);
			NodeInfo n = find_successor(keyID, result);
			if(n != this.node){
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+n.ipaddress+"/ChordNode_" + n.port);
				result.hopCount++;				
				return c.delete_key_local(keyID, key, result);
			} else{
				return delete_key_local(keyID, key, result);
			}		
		} catch (Exception e) {
			log.error("Error in deleting key"+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
			return false;
		}
	}

	@Override
	/**
	 * Wrapper function to get value associated to a key
	 * @param key Key for which data is to be retrieved
	 * @param result result object to assist in metrics collection
	 * @param val The data associated to the key
	 */
	public String get_value(String key, Result result) {
		try {
			long endTime,startTime,timetaken;
			startTime = System.currentTimeMillis();
			int keyID = generate_ID(key, maxNodes);
			NodeInfo n = find_successor(keyID, result);
			if(n != this.node){
				ChordNode c = (ChordNode)Naming.lookup("rmi://"+n.ipaddress+"/ChordNode_" + n.port);
				result.hopCount++;
				String val = c.get_key_local(keyID, key, result);
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				log.info("Time taken for query key operation: " + timetaken + "ms");
				totalLatency_query += timetaken;
				totalHopCount_query += result.hopCount;
				return val;
			} else{
				String val = get_key_local(keyID, key, result);
				endTime = System.currentTimeMillis();
				timetaken = endTime - startTime;
				log.info("Time taken for query key operation: " + timetaken + "ms");
				totalLatency_query += timetaken;
				totalHopCount_query += result.hopCount;
				return val;
			}			
		} catch (Exception e) {
			log.error("Error in get value of key"+e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(), e);
			return null;
		}
	}
	
	@Override
	/**
	 * Function to insert key value pair in current Chord Node instance
	 * @param keyID Hashed valued for the key
	 * @param key Key to be inserted
	 * @param value Value associated to the key
	 * @param result result object to assist in metric collection
	 * @param boolean Indiator to check if operation was successful or not
	 */
	public boolean insert_key_local(int keyID, String key, String value, Result result) throws RemoteException {
		boolean res = true;
		
		data_rwlock.writeLock().lock();
		
		if(!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
			// keyID does not lie in my range.
			// Maybe ring topology has changed while this request was routed to me.
			// Query the ring again for this key.
			data_rwlock.writeLock().unlock();
			res = insert_key(key, value, result);
		} else {
			HashMap<String, String> entry = data.get(keyID);
			if(entry == null) {
				entry = new HashMap<String, String>();
				data.put(keyID, entry);
			}
			entry.put(key, value);
			
			data_rwlock.writeLock().unlock();
			
			log.info("Inserted key - " + key + " with value - " + value);
		}
		return res;
	}
	
	@Override
	/**
	 * Function to delete key value pair in current Chord Node instance
	 * @param keyID Hashed valued for the key
	 * @param key Key to be deleted
	 * @param result result object to assist in metric collection
	 * @param boolean Indiator to check if operation was successful or not
	 */
	public boolean delete_key_local(int keyID, String key, Result result) throws RemoteException {
		boolean res = true;
		
		data_rwlock.writeLock().lock();
		if(!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
			// keyID does not lie in my range.
			// Maybe ring topology has changed while this request was routed to me.
			// Query the ring again for this key.
			data_rwlock.writeLock().unlock();
			res = delete_key(key, result);
		} else {
			HashMap<String, String> entry = data.get(keyID);
			if(entry != null) 
				if(entry.get(key) != null) {
					entry.remove(key);
					log.info("Deleted key - " + key);
				} else {
					res = false;
				}
			data_rwlock.writeLock().unlock();
		}
		return res;
	}
	
	@Override
	/**
	 * Function to retieve key value pair in current Chord Node instance
	 * @param keyID Hashed valued for the key
	 * @param key Key to be retrieved
	 * @param result result object to assist in metric collection
	 * @param boolean Indiator to check if operation was successful or not
	 */
	public String get_key_local(int keyID, String key, Result result) throws RemoteException {
		String val = null;
		
		data_rwlock.readLock().lock();
		
		HashMap<String, String> entry = data.get(keyID);
		if(entry != null) 
			val = entry.get(key);
		
		data_rwlock.readLock().unlock();
		
		// Key does not lie in my range.
		// Maybe the key migrated due to a recent node join, 
		// and this is an old query that has reached me late.
		// Query the ring again for this key.
		if(entry == null && !inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID))
			val = get_value(key, result);
		
		return val;
	}
	
	@Override
	/**
	 * This function is called when a CHord Node leaves the ring
	 * @param result result object to assist in metric collection
	 * @return boolean Status of leave operation
	 */
	public boolean leave_ring(Result result) throws RemoteException {
		ChordNode c = null;
		data_rwlock.writeLock().lock();
		
		/* Order of operations when a node leaves the ring: 
		1) Migrate keys to successor.
		2) Inform successor.
		3) Inform predecessor.
		4) Inform bootstrap.
		5) Inform other nodes.
		
		Note: Any key-related requests that are routed to this node 
		between steps (1) and (3) will fail.
		*/
		
		try {
			c = (ChordNode)Naming.lookup("rmi://" + this.get_successor().ipaddress + "/ChordNode_" + this.get_successor().port);
			
			for(Map.Entry<Integer, HashMap<String, String>> hashkeys: data.entrySet()){
				int key = hashkeys.getKey();
				for(Map.Entry<String, String> e: hashkeys.getValue().entrySet()){
					log.info("Migrating - HashKey: " + key + "\t Key: " + e.getKey() + "\t Value: " + e.getValue());
					c.insert_key_local(key, e.getKey(), e.getValue(), result);	
					if(this.node.nodeID != this.get_successor().nodeID)
						result.hopCount++;
				}
			}
			
			log.info("Key migration on leaving done");	
			data.clear(); 
			log.debug("Data cleared before leaving");
			
		} catch (Exception e) {
			log.error(e);
			return false;
		} finally {
			data_rwlock.writeLock().unlock();
		}	
		
		try {
			//Set successor's predecessor to my predecessor
			if(this.node.nodeID != this.get_successor().nodeID)
				result.hopCount++;
			c.set_predecessor(this.get_predecessor());
			
			//Set predecessor's successor to my successor
			c = (ChordNode)Naming.lookup("rmi://" + this.predcessor.ipaddress + "/ChordNode_" + this.predcessor.port);
			if(this.node.nodeID != this.get_successor().nodeID)
				result.hopCount++;
			c.set_successor(this.get_successor());
			
			//Inform bootstrap and other chord nodes of departure
			bootstrap.removeNodeFromRing(this.node);
			update_others_before_leave(result);
					
		} catch (Exception e) {
			log.error(e);
		} 
		
		return true;
	}
	
	@Override
	/**
	 * This function is used to generate a unique identifier for a key using SHA-1 algorithm
	 * @param key Key for which identifier is to be generated
	 * @param maxNodes Maximum no of nodes in the Chord ring
	 * @return int unique identifier for the key
	 */
	public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.reset();		
        byte[] hashBytes = md.digest(key.getBytes());
        BigInteger hashValue = new BigInteger(1,hashBytes);
        log.debug("key:" + key + " hash:" + Math.abs(hashValue.intValue()) % maxNodes);
        return Math.abs(hashValue.intValue()) % maxNodes;
	}

	/**
	 * This function is used to move keys in range (predecessor, n) to successor node
	 * @param pred Predecessor nodeinfo object
	 * @param newNode The current instances nodeinfo object
	 * @param result Result object to assist in metrics collection
	 * @return null
	 */	  
	public void migrate_keys(NodeInfo pred, NodeInfo newNode, Result result) throws RemoteException{
		ArrayList<Integer> removelist = new ArrayList<Integer>();
		
		data_rwlock.writeLock().lock();
		
		for(Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()){
			int key = hashkeys.getKey();
			if(this.inCircularIntervalEndInclude(key, pred.nodeID, newNode.nodeID)){			
				for(Map.Entry<String,String> e : hashkeys.getValue().entrySet()){
					//System.out.println("HashKey: " + key);
					//System.out.print("\tKey: " + e.getKey());
					//System.out.print("\tValue: " + e.getValue());
					try {
						ChordNode c = (ChordNode)Naming.lookup("rmi://" + newNode.ipaddress + "/ChordNode_" + newNode.port);
						c.insert_key_local(key, e.getKey(), e.getValue(), result);
					} catch (Exception e1) {
						e1.printStackTrace();
					} 
				}
				//Remove the key from the current node
				removelist.add(key);			
			}			
		}
		for(Integer i : removelist){
			data.remove(i);
		}
		data_rwlock.writeLock().unlock();
	}

	/**
	 * Function to display the data stored in the current Chord Node instance
	 * @param null
	 * @return null
	 */
	public void display_data_stored() throws RemoteException{
		for(Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()){
			int key = hashkeys.getKey();
			for(Map.Entry<String,String> e : hashkeys.getValue().entrySet()){
				System.out.print("Hash Key: " + key);
				System.out.print("\tActual Key: " + e.getKey());
				System.out.println("\tActual Value: " + e.getValue());
			}
		}			
	}

	/**
	 * Dummy function to assist in latency calculation during node joins
	 * @param n Nodeinfo object of node which is to be called
	 * @return null
	 */
	public void makeCall(NodeInfo n) throws RemoteException{
		if(n != null){
			ChordNode c = null;
			try {
				c = (ChordNode)Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
				c.send_beat();
			} catch (Exception e) {
			
				e.printStackTrace();
			}
		}
	}
	
	public long get_insert_latency() throws RemoteException{
		return totalLatency_insert;
	}
	
	public long get_query_latency() throws RemoteException{
		return totalLatency_query;
	}
	
	public long get_join_time() throws RemoteException{
		return totalLatency_join;
	}

	public int get_insert_hopcount() throws RemoteException{
		return totalHopCount_insert;
	}

	public int get_query_hopcount() throws RemoteException{
		return totalHopCount_query;
	}

	public int get_join_hopcount() throws RemoteException{
		return totalHopCount_join;
	}
}
