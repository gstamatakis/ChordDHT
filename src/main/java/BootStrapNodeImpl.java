/**
 * This class serves as the starting point for the BootStrap server and 
 * has functions to assist in node joins and departure while also serves 
 * the purpose of collecting metrics for the improvements made.
 */

import java.math.BigInteger;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Date;
import java.text.SimpleDateFormat;

public class BootStrapNodeImpl extends UnicastRemoteObject implements BootStrapNode{
	private static final long serialVersionUID = 1L;
	
	// m and maxNodes in BootStrapNodeImpl should be same as
	// m and maxNodes in ChordNodeImpl.
	public static int maxNodes = 32; // Maximum number of permitted nodes in the Chord Ring
	public static int m = 5; // maxNodes = 2^m;
	public static int noOfNodes = 0;
	
	// For testing module
	public static int testNodeCount = 0;
	public static int testQueryKeyCount = 0;
	public static int testKeyCount = 0;
	public static long totalNodeJoinTime = 0;
	public static String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
	public static ArrayList<String> TestKeysList = new ArrayList<String>();
	public static long[] operationStat= new long[3];
	public static ArrayList<Result> stats = new ArrayList<Result>();
	public static ArrayList<Result> stats2 = new ArrayList<Result>(); 
	public static boolean[] testOpsStat;
	public static boolean[] testOpsStat2;
	public static boolean testGetOps;
	public static long errcount_query = 0;
	
	// Variables to identify the nodes in the Chord Ring
	public static HashMap<Integer, NodeInfo> nodes = new HashMap<Integer, NodeInfo>();
	public static ArrayList<NodeInfo> nodeList = new ArrayList<NodeInfo>();
	public static ArrayList<Integer> nodeIds = new ArrayList<Integer>();
	
	/**
	 * Dummy constructor
	 * @param null
	 * @return null
	 */
	public BootStrapNodeImpl() throws RemoteException {
		System.out.println("Bootstrap Node created");
	}

	/**
	 * This function will be called by any Chord Node that wishes to join the network. The main objective of this function is to assign a unique identifier to a new Chord Node and add it the the ring. Other than this it also implements the two different methods on how to be assigned identifiers based on network proximity.
	 * @param ipaddress The IP Address of the Chord Node joining the ring
	 * @param port The port/identifier of Chord Node instance joining the ring. This differentiates multiple instance running on same system
	 * @param zoneID The identifier for the zone. Accepted values are from -1 to m. Specifying -1 will make the BootStrap assign identifier based on proximity to its probable successors and predecessors.
	 */
	public ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port, int zoneID) throws RemoteException{
		synchronized(this){
			if(nodeList.size() == maxNodes){
				System.out.println("No more node joins allowed as Chord network has reached it capacity");
				return null;
			} else{
				ArrayList<NodeInfo> result = new ArrayList<NodeInfo>();
				ArrayList<Integer> copy = nodeIds;
				noOfNodes++;
				int nodeID = -1;
				String timeStamp = "";
				ArrayList<Integer> randomIds = null;//Stores the set of random Ids generated for the network proximity method
				ArrayList<Integer> succIds = null;
				ArrayList<Integer> predIds = null;
				if(zoneID < 0){//If zoneID < 0, then opt for the proximity based identifier assignment method for node identifier assignment
					randomIds = new ArrayList<Integer>();
					succIds = new ArrayList<Integer>();
					predIds = new ArrayList<Integer>();

					int i;
					int freeZoneCnt = 0;
					for(i = 0; i < m; i++){//For each zone in the ring
						boolean isFilled = isZoneFilled(i);
						if(!isFilled){//If zone is not completely filled, then generate a random ID in the corresponding zone range
							freeZoneCnt++;
							boolean repeat = true;
							while(repeat){
								timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
								try {
									nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
								} catch (NoSuchAlgorithmException e) {
									e.printStackTrace();
								}
								if(nodeID >= i * m && nodeID < (i+1) * m && nodeIds.indexOf(nodeID) == -1 && randomIds.indexOf(nodeID) == -1){
									repeat = false;
								}
							} 
							randomIds.add(nodeID);
							copy.add(nodeID);
							Collections.sort(copy);
							succIds.add(nodeIds.get((nodeIds.indexOf(nodeID) + 1) % noOfNodes));
							predIds.add(nodeIds.get((nodeIds.indexOf(nodeID) - 1 + noOfNodes) % noOfNodes));
							copy.remove(new Integer(nodeID));
						}
					}
					//If only one zone was found to be free directly add to the nodeIds list
					if(freeZoneCnt == 1){
						NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
						nodes.put(nodeID, ni);
						nodeIds.add(nodeID);
						nodeList.add(ni);
						System.out.println("New node added to ring with ID: " + nodeID);                
					}else{//Calculate the latency for each probable ID and choose the best
						int j, k;
						int minLatencyIdx = 0;
						long minLatency = Long.MAX_VALUE;
						for(k = 0; k < randomIds.size(); k++){
							int node_id = randomIds.get(k);
							int succ_id = succIds.get(k);
							int pred_id = predIds.get(k);
							long startTime = System.currentTimeMillis();
							ChordNode c = null;
							try {
								c = (ChordNode)Naming.lookup("rmi://" + ipaddress + "/ChordNode_" + port);
							} catch (MalformedURLException e) {
								e.printStackTrace();
							} catch (NotBoundException e) {
								e.printStackTrace();
							}
							c.makeCall(nodes.get(succ_id));
							long endTime = System.currentTimeMillis();
							long timetaken = endTime - startTime;
							if(timetaken < minLatency){
								minLatency = timetaken;
								minLatencyIdx = k;
							}
						}
						NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
						nodes.put(nodeID, ni);
						nodeIds.add(nodeID);
						nodeList.add(ni);
						System.out.println("New node added to ring with ID: " + nodeID); 
					}
				} else{//If zoneID >= 0, then opt for topology aware zone selection mechanism for node identifier assignment
					//Ensure that even if zoneID is entered incorrectly by the user, it falls withing the expected range.
					if(zoneID < 0 || zoneID >= m){
						zoneID = zoneID % m;//This does the trick.
					}
					boolean isZoneFilled = isZoneFilled(zoneID);
					try{
						if(!isZoneFilled){//If zone is not full, then generate an ID in its range and assign
							do {
								//Keep generating a new nodeID so long as it does not fall within the the range specified for the zone ID.
								timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
								nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
							} while(nodeID < m * zoneID || nodeID >= m * (zoneID + 1) || nodeIds.indexOf(nodeID) != -1);
						}else{//If zone is full, then assign a random ID from other zones
							do {
								timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
								nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
							} while(nodeIds.indexOf(nodeID) != -1);
						}
						if(nodeIds.indexOf(nodeID) == -1){
							NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
							nodes.put(nodeID, ni);
							nodeIds.add(nodeID);
							nodeList.add(ni);
						}
						System.out.println("New node added to ring with ID: " + nodeID);                
					}catch (Exception e) {
						System.out.println("Error in hashing function");
						e.printStackTrace();
						return null;
					}
				}				
				
				Collections.sort(nodeIds);
				int successor = nodeIds.get((nodeIds.indexOf(nodeID) + 1) % noOfNodes);//Get the successor node
				System.out.println("Successor for new node: " + successor);
				int predecessor = nodeIds.get((nodeIds.indexOf(nodeID) - 1 + noOfNodes) % noOfNodes);//Get the predecessor node
				System.out.println("Predecessor for new node: " + predecessor);
				
				result.add(nodes.get(nodeID));
				result.add(nodes.get(successor));
				result.add(nodes.get(predecessor));

				//This section contains the code to test the protocol on deploying in multiple systems and has entire metrics collection 
				//modules too as part of it
				if(noOfNodes == testNodeCount){					
					testOpsStat = new boolean[3];
					testOpsStat[0]=false;
					testOpsStat[1]=false;
					testOpsStat[2]=false;
					testGetOps = false;
					testOpsStat2 = new boolean[3];
					testOpsStat2[0]=false;
					testOpsStat2[1]=false;
					testOpsStat2[2]=false;
					
					totalNodeJoinTime = 0;
					
					for(int i=0;i<noOfNodes;i++){
						try {
							Result insHops = new Result();
							ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(i).ipaddress + "/ChordNode_" + nodeList.get(i).port);
							totalNodeJoinTime += c.get_join_time();
						} catch (MalformedURLException | RemoteException | NotBoundException e) {
							e.printStackTrace();
							//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
						}
					}
					
					//create 3 threads(t1-t3) to insert keys concurrently from multiple nodes
					Thread t1 = new Thread(new Runnable() {
					public void run()
					{
						// 
						try {
							Result insHops = new Result();
							ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(0).ipaddress + "/ChordNode_" + nodeList.get(0).port);
							int testOps=0;
							for(int i=0;i<testKeyCount/3;i++){
								System.out.println("THREAD 1 : Inserting Key : "+TestKeysList.get(i));
								c.insert_key(TestKeysList.get(i), TestKeysList.get(i), insHops);
								testOps++;
							}
							insHops.latency = c.get_insert_latency();
							insHops.hopCount = c.get_insert_hopcount();
							System.out.println("Got latency from thread 1 : "+insHops.latency);
							stats.add(insHops);
							testOpsStat[0]=true;
						} catch (MalformedURLException | RemoteException | NotBoundException e) {
							e.printStackTrace();
							//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
						}
					}});  
					Thread t2 = new Thread(new Runnable() {
						public void run()
						{
							try {
								Result insHops = new Result();
								ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(1).ipaddress + "/ChordNode_" + nodeList.get(1).port);
								int testOps=0;
								for(int i=(testKeyCount/3);i<((2*testKeyCount)/3);i++){
									System.out.println("THREAD 2 : Inserting Key : "+TestKeysList.get(i));
									c.insert_key(TestKeysList.get(i), TestKeysList.get(i), insHops);
									testOps++;
								}
								insHops.latency = c.get_insert_latency();
								insHops.hopCount = c.get_insert_hopcount();
								System.out.println("Got latency from thread 2 : "+insHops.latency);
								stats.add(insHops);
								testOpsStat[1]=true;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
								//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
							}
						}});  
					Thread t3 = new Thread(new Runnable() {
						public void run()
						{
							// 
							try {
								Result insHops = new Result();
								ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(2).ipaddress + "/ChordNode_" + nodeList.get(2).port);
								int testOps=0;
								for(int i=((2*testKeyCount)/3);i<testKeyCount;i++){
									System.out.println("THREAD 3 : Inserting Key : "+TestKeysList.get(i));
									c.insert_key(TestKeysList.get(i), TestKeysList.get(i), insHops);
									testOps++;
								}
								insHops.latency = c.get_insert_latency();
								insHops.hopCount = c.get_insert_hopcount();
								System.out.println("Got latency from thread 2 : "+insHops.latency);
								stats.add(insHops);
								testOpsStat[2]=true;
							} catch (MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
								//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
							}
						}}); 
					//Thread t4 collects the metrics for insert operation performed by previous 3 threads
					Thread t4 = new Thread(new Runnable() {
						public void run()
						{
							try {
								while(!(testOpsStat[0] && testOpsStat[1] && testOpsStat[2])){	
									Thread.sleep(5000);
									System.out.println("Key Insertion Operation Still happening");
								}
								System.out.println("Key Operation completed");
								long totalHops = 0;
								long totalTime = 0;
								for(int i=0;i<stats.size();i++){
									totalTime += stats.get(i).latency;
									totalHops += stats.get(i).hopCount;
								}
								operationStat[0] = totalTime;
								operationStat[2] = totalHops;//insert hop count
								testOpsStat[0]=false;
								testOpsStat[1]=false;
								testOpsStat[2]=false;
								testGetOps = true;
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}}); 
					//Threads t5, t6 and t7 perform the get key operation concurrently
					Thread t5 = new Thread(new Runnable() {
						public void run()
						{
							try {
								while(!testGetOps){
									Thread.sleep(5000);
								}
								Result insHops = new Result();
								ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(0).ipaddress + "/ChordNode_" + nodeList.get(0).port);
								int testOps=0;
								for(int i=0;i<testQueryKeyCount/3;i++){
									System.out.println("THREAD 1 : Get Key : "+TestKeysList.get(i));
									if(!TestKeysList.get(i).equals(c.get_value(TestKeysList.get(i), insHops)))
										errcount_query++;
									testOps++;
								}
								insHops.latency = c.get_query_latency();
								insHops.hopCount = c.get_query_hopcount();
								System.out.println("Got Query latency from thread 1 : "+insHops.latency);
								stats2.add(insHops);
								testOpsStat2[0]=true;
							} catch (InterruptedException | MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
								//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
							}
						}});
					
					Thread t6 = new Thread(new Runnable() {
						public void run()
						{
							try {
								while(!testGetOps){
									Thread.sleep(5000);
								}
								Result insHops = new Result();
								ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(1).ipaddress + "/ChordNode_" + nodeList.get(1).port);
								int testOps=0;
								for(int i=(testQueryKeyCount/3);i<((2*testQueryKeyCount)/3);i++){
									System.out.println("THREAD 2 : Get Key : "+TestKeysList.get(i));
									if(!TestKeysList.get(i).equals(c.get_value(TestKeysList.get(i), insHops)))
										errcount_query++;
									testOps++;
								}
								insHops.latency = c.get_query_latency();
								insHops.hopCount = c.get_query_hopcount();
								System.out.println("Got Query latency from thread 2 : "+insHops.latency);
								stats2.add(insHops);
								testOpsStat2[1]=true;
							} catch (InterruptedException | MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
								//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
							}
						}});
					
					Thread t7 = new Thread(new Runnable() {
						public void run()
						{
							// 
							try {
								while(!testGetOps){
									Thread.sleep(5000);
								}
								Result insHops = new Result();
								ChordNode c = (ChordNode)Naming.lookup("rmi://" + nodeList.get(2).ipaddress + "/ChordNode_" + nodeList.get(2).port);
								int testOps=0;
								for(int i=((2*testQueryKeyCount)/3);i<testQueryKeyCount;i++){
									System.out.println("THREAD 3 : Get Key : "+TestKeysList.get(i));
									if(!TestKeysList.get(i).equals(c.get_value(TestKeysList.get(i), insHops)))
										errcount_query++;
									testOps++;
								}
								insHops.latency = c.get_query_latency();
								insHops.hopCount = c.get_query_hopcount();
								System.out.println("Got Query latency from thread 3 : "+insHops.latency);
								stats2.add(insHops);
								testOpsStat2[2]=true;
							} catch (InterruptedException | MalformedURLException | RemoteException | NotBoundException e) {
								e.printStackTrace();
								//log.error(e.getClass() + ": " +  e.getMessage() + ": " + e.getCause() + "\n" +  e.getStackTrace().toString(),e);
							}
						}});
					//Thread t8 collects metrics for get opeations and displays results for node joins, insert and get operation
					Thread t8 = new Thread(new Runnable() {
						public void run()
						{
							try {
								while(!(testOpsStat2[0] && testOpsStat2[1] && testOpsStat2[2])){	
									Thread.sleep(5000);
									System.out.println("Key Query Operation Still happening");
								}
								System.out.println("Key Operation completed");
								long totalTime = 0;
								long totalHopsQuery = 0;
								for(int i=0;i<stats2.size();i++){
									totalTime += stats2.get(i).latency;
									totalHopsQuery += stats2.get(i).hopCount;
								}
								
								// join latency
								System.out.println("\n##########################");
								System.out.println("Total latency for "+noOfNodes+" node joins is : "+totalNodeJoinTime+"ms");
								System.out.println("Average latency per node join for "+noOfNodes+" is : "+(totalNodeJoinTime/noOfNodes)+"ms");
								System.out.println("##########################\n");
								
								// Insert Operations
								System.out.println("\n##########################");
								System.out.println("Total latency for "+testKeyCount+" keys insert is : "+operationStat[0]+"ms");
								System.out.println("Average latency per key insertion for "+testKeyCount+" keys is : "+(operationStat[0]/testKeyCount)+"ms");
								System.out.println("Average communication messages per key insertion for "+testKeyCount+" keys is : "+(operationStat[2]/testKeyCount));
								System.out.println("##########################\n");
								
								// Query latency
								operationStat[1] = totalTime;
								System.out.println("\n##########################");
								System.out.println("Total latency for "+testQueryKeyCount+" keys query is : "+operationStat[1]+"ms");
								System.out.println("Average latency per key query for "+testQueryKeyCount+" keys is : "+(operationStat[1]/testQueryKeyCount)+"ms");
								System.out.println("Average communication messages per key query for "+testQueryKeyCount+" keys is : "+(totalHopsQuery/testQueryKeyCount));
								System.out.println("No of key hits: " + (testQueryKeyCount - errcount_query));
								System.out.println("No of key misses: " + errcount_query);
								System.out.println("##########################\n");
								testOpsStat2[0]=false;
								testOpsStat2[1]=false;
								testOpsStat2[2]=false;
								testGetOps = true;
								stats = new ArrayList<Result>(); 
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}}); 
					
					t1.start();
					t2.start();
					t3.start();
					t4.start();
					t5.start();
					t6.start();
					t7.start();
					t8.start();
				}
				
				return result;
			}			
		}
	}
	
	/**
	 * This function is called when a Chord Node leaves the ring or is found to be dead.
	 * @param n NodeInfo object storing details of the Chord Node instance
	 * @return null
	 */
	public void removeNodeFromRing(NodeInfo n) throws RemoteException{
		synchronized(this){
			if(n == null || nodes.get(n.nodeID) == null)
				return;
			nodeList.remove(nodes.get(n.nodeID));
			System.out.println("Updated node list");
			nodeIds.remove(new Integer(n.nodeID));
			System.out.println("Updated node ID list");
			nodes.remove(n.nodeID);
			noOfNodes--;
			System.out.println("Node " + n.nodeID + " left Chord Ring");
			System.out.println("Number of nodes in Chord Ring: " + noOfNodes);
			displayNodesInRing();
		}		
	}	
	
	/**
	 * This function finds the new successor node if the current successor node is found to be dead or has crashed.
	 * @param n NodeInfo object storing details of the Chord Node instance whose successor is to be found
	 * @param dead_node NodeInfo object of the Chord Node instance which is dead
	 * @return succ NodeInfo object of the new successor Chord Node
	 */
	public NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException {
		NodeInfo succ = null;
		System.out.println("Received update from node " + n.nodeID + " that node " + dead_node.nodeID + " is dead.");
		try {
			removeNodeFromRing(dead_node);
		} catch(Exception e){
			System.out.println("There is some problem with Removing dead node " + dead_node.nodeID + ": " + e.getMessage());	
		}
		
		int successor = nodeIds.get((nodeIds.indexOf(n.nodeID) + 1) % noOfNodes);
		System.out.println("Assigning new successor " + successor + " to node " + n.nodeID);
		succ = nodes.get(successor);
		return succ;
	}
	
	/**
	 * This function is called when a Chord Node has successfully joined the Chord ring
	 * @param nodeID The node identifier of the Chord Node that joined successfully
	 * @return null
	 */
	public void acknowledgeNodeJoin(int nodeID) throws RemoteException {
		synchronized(this){
			System.out.println("Join acknowledge: New node joined Chord Ring with identifier " + nodeID);
			System.out.println("Number of nodes in Chord Ring: " + noOfNodes);
			displayNodesInRing();	
		}			
	}
	
	/** 
	 * This function displays the details of the Chord Nodes in the ring
	 * @param null
	 * @return null
	 */
	public void displayNodesInRing() throws RemoteException{
		Iterator<NodeInfo> i = nodeList.iterator();
		System.out.println("*********************List of nodes in the ring********************");
		while(i.hasNext()){
			NodeInfo ninfo = i.next();
			System.out.println("Node ID: " + ninfo.nodeID);
			System.out.println("Node IP: " + ninfo.ipaddress);
			System.out.println("Node Port: " + ninfo.port);
			System.out.println("******************\n");
		}
	}
	
	/** 
	 * This function is the starting point for the BootStrap server
	 * @param args Variable length command line arguments
	 * @return null
	 */
	public static void main(String []args) throws Exception{
		if(args.length>0){
			// testing module params 
			testNodeCount = new Integer(args[0]).intValue();
			testKeyCount = new Integer(args[1]).intValue();
			testQueryKeyCount = new Integer(args[2]).intValue();
			for(int i=0;i<testKeyCount;i++){
				Random rand=new Random();
			    StringBuilder res=new StringBuilder();
			    // generate 20 character keys and values
			    for (int j = 0; j < 20; j++) {
			       int randIndex = rand.nextInt(36); 
			       res.append(SALTCHARS.charAt(randIndex));            
			    }
			    TestKeysList.add(res.toString());
			}
		}		
		
		try{
			BootStrapNodeImpl bnode = new BootStrapNodeImpl();
			//System.setProperty("java.rmi.server.hostname","127.0.0.1");
			Naming.rebind("ChordRing", bnode);
			noOfNodes = 0;
			System.out.println("Waiting for nodes to join or leave the Chord Ring");
			System.out.println("Number of nodes in Chord Ring: " + noOfNodes + "\n");
		} catch(Exception e){
			System.out.println("There is some problem with Bootstrap Node." + e.getMessage());			
		}	
	}
	
	/**
	 * This function returns the number of nodes currently in the ring
	 * @param null
	 * @return int Number of nodes in the ring
	 */
	public int getNodesInRing() throws RemoteException {
		return nodeList.size();
	}

	/**
	 * Getter function for the list of node identifiers assigned
	 * @param null
	 * @return ArrayList List of the node identifiers already assigned
	 */
	public ArrayList<Integer> getNodeIds() throws RemoteException {
		return nodeIds;
	}

	@Override
	/**
	 * This function return the maximum possible nodes allowed in the Chord ring
	 * @param null
	 * @return int Maximum number of nodes allowed in the ring
	 */
	public int getMaxNodesInRing() throws RemoteException {
		return maxNodes;
	}
	
	@Override
	/** 
	 * The function generates a unique identifier for the Chord Node instance using SHA-1 algorithm
	 * @param key The key with which the unique identifer is to be generated using SHA-1
	 * @param maxNodes The maximum number of nodes in the ring
	 * @return int Unique identifer for the Chord Node
	 */
	public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		md.reset();		
		md.update((key).getBytes());
        byte[] hashBytes = md.digest();
        BigInteger hashValue = new BigInteger(1,hashBytes);
        return Math.abs(hashValue.intValue()) % maxNodes;
	}

	/**
	 * This function checks if a particular zone of the Chord ring is full ,i.e. all the slots are occupied by CHord Node instances
	 * @param zoneID The zone number in the Chord ring. Allowed range is 0 to (m-1).
	 * @return is_filled Boolean value indicating is zone is full or not. True indicates full.
	 */
	public boolean isZoneFilled(int zoneID) throws RemoteException{
		int i;
		boolean is_filled = true;
		for(i = 0; i < m; i++){
			if(nodeIds.indexOf(zoneID * m + i) < 0){
				is_filled = false;
				break;
			}
		}
		return is_filled;
	}
}
