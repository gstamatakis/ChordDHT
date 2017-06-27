/*
 * This class serves as the starting point for the BootStrap server and
 * has functions to assist in node joins and departure while also serves
 * the purpose of collecting metrics for the improvements made.
 */

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BootStrapNodeImpl extends UnicastRemoteObject implements BootStrapNode {
    private static final long serialVersionUID = 1L;

    public static int m = 3;
    public static int maxNodes = (int) Math.pow(2.0, (long) m);         // Maximum number of permitted nodes in the Chord Ring
    public static HashMap<Integer, NodeInfo> nodes = new HashMap<>();   // Variables to identify the nodes in the Chord Ring
    private static int noOfNodes = 0;
    private static ArrayList<NodeInfo> nodeList = new ArrayList<>();
    private static ArrayList<Integer> nodeIds = new ArrayList<>();

    /**
     * Dummy constructor
     *
     * @throws RemoteException Due to RMI.
     */
    public BootStrapNodeImpl() throws RemoteException {
        System.out.println("Bootstrap Node created");
    }

    /**
     * This function is the starting point for the BootStrap server
     *
     * @param args Variable length command line arguments
     * @throws RemoteException Due to RMI.
     */
    public static void main(String[] args) throws Exception {
        try {
            BootStrapNodeImpl bnode = new BootStrapNodeImpl();
            Naming.rebind("ChordRing", bnode);
            noOfNodes = 0;
            System.out.println("Waiting for nodes to join or leave the Chord Ring");
            System.out.println("Number of nodes in Chord Ring: " + noOfNodes + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<NodeInfo> addNodeToRing(String ipaddress, String port, int zoneID) throws RemoteException {
        synchronized (this) {
            if (nodeList.size() == maxNodes) {
                System.out.println("No more node joins allowed as Chord network has reached it capacity");
                return null;
            } else {
                ArrayList<NodeInfo> result = new ArrayList<>();
                ArrayList<Integer> copy = nodeIds;
                noOfNodes++;
                int nodeID = -1;
                String timeStamp;
                ArrayList<Integer> randomIds;//Stores the set of random Ids generated for the network proximity method
                ArrayList<Integer> succIds;
                ArrayList<Integer> predIds;
                if (zoneID < 0) {//If zoneID < 0, then opt for the proximity based identifier assignment method for node identifier assignment
                    randomIds = new ArrayList<>();
                    succIds = new ArrayList<>();
                    predIds = new ArrayList<>();

                    int i;
                    int freeZoneCnt = 0;
                    for (i = 0; i < m; i++) {//For each zone in the ring
                        boolean isFilled = isZoneFilled(i);
                        if (!isFilled) {//If zone is not completely filled, then generate a random ID in the corresponding zone range
                            freeZoneCnt++;
                            boolean repeat = true;
                            while (repeat) {
                                timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
                                try {
                                    nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                }
                                if (nodeID >= i * m && nodeID < (i + 1) * m && nodeIds.indexOf(nodeID) == -1 && randomIds.indexOf(nodeID) == -1) {
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
                    if (freeZoneCnt == 1) {
                        NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
                        nodes.put(nodeID, ni);
                        nodeIds.add(nodeID);
                        nodeList.add(ni);
                        System.out.println("New node added to ring with ID: " + nodeID);
                    } else {//Calculate the latency for each probable ID and choose the best
                        int k;
                        long minLatency = Long.MAX_VALUE;
                        for (k = 0; k < randomIds.size(); k++) {
                            randomIds.get(k);
                            int succ_id = succIds.get(k);
                            predIds.get(k);
                            long startTime = System.currentTimeMillis();
                            ChordNode c = null;
                            try {
                                c = (ChordNode) Naming.lookup("rmi://" + ipaddress + "/ChordNode_" + port);
                            } catch (NotBoundException | MalformedURLException e) {
                                e.printStackTrace();
                            }
                            assert c != null;
                            c.makeCall(nodes.get(succ_id));
                            long endTime = System.currentTimeMillis();
                            long timetaken = endTime - startTime;
                            if (timetaken < minLatency) {
                                minLatency = timetaken;
                            }
                        }
                        NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
                        nodes.put(nodeID, ni);
                        nodeIds.add(nodeID);
                        nodeList.add(ni);
                        System.out.println("New node added to ring with ID: " + nodeID);
                    }
                } else {//If zoneID >= 0, then opt for topology aware zone selection mechanism for node identifier assignment
                    //Ensure that even if zoneID is entered incorrectly by the user, it falls withing the expected range.
                    if (zoneID < 0 || zoneID >= m) {
                        zoneID = zoneID % m;//This does the trick.
                    }
                    boolean isZoneFilled = isZoneFilled(zoneID);
                    try {
                        if (!isZoneFilled) {//If zone is not full, then generate an ID in its range and assign
                            do {
                                //Keep generating a new nodeID so long as it does not fall within the the range specified for the zone ID.
                                timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
                                nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
                            }
                            while (nodeID < m * zoneID || nodeID >= m * (zoneID + 1) || nodeIds.indexOf(nodeID) != -1);
                        } else {//If zone is full, then assign a random ID from other zones
                            do {
                                timeStamp = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss.SSS").format(new Date());
                                nodeID = generate_ID(ipaddress + port + timeStamp, maxNodes);
                            } while (nodeIds.indexOf(nodeID) != -1);
                        }
                        if (nodeIds.indexOf(nodeID) == -1) {
                            NodeInfo ni = new NodeInfo(ipaddress, port, nodeID);
                            nodes.put(nodeID, ni);
                            nodeIds.add(nodeID);
                            nodeList.add(ni);
                        }
                        System.out.println("New node added to ring with ID: " + nodeID);
                    } catch (Exception e) {
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
                return result;
            }
        }
    }

    public void removeNodeFromRing(NodeInfo n) throws RemoteException {
        synchronized (this) {
            if (n == null || nodes.get(n.nodeID) == null)
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

    public NodeInfo findNewSuccessor(NodeInfo n, NodeInfo dead_node) throws RemoteException {
        NodeInfo succ;
        System.out.println("Received update from node " + n.nodeID + " that node " + dead_node.nodeID + " is dead.");
        try {
            removeNodeFromRing(dead_node);
        } catch (Exception e) {
            System.out.println("There is some problem with Removing dead node " + dead_node.nodeID + ": " + e.getMessage());
        }

        int successor = nodeIds.get((nodeIds.indexOf(n.nodeID) + 1) % noOfNodes);
        System.out.println("Assigning new successor " + successor + " to node " + n.nodeID);
        succ = nodes.get(successor);
        return succ;
    }

    public void acknowledgeNodeJoin(int nodeID) throws RemoteException {
        synchronized (this) {
            System.out.println("Join acknowledge: New node joined Chord Ring with identifier " + nodeID);
            System.out.println("Number of nodes in Chord Ring: " + noOfNodes);
            displayNodesInRing();
        }
    }

    public void displayNodesInRing() throws RemoteException {
        Iterator<NodeInfo> i = nodeList.iterator();
        System.out.println("*********************List of nodes in the ring********************");
        while (i.hasNext()) {
            NodeInfo ninfo = i.next();
            System.out.println("Node ID: " + ninfo.nodeID);
            System.out.println("Node IP: " + ninfo.ipaddress);
            System.out.println("Node Port: " + ninfo.port);
            System.out.println("******************\n");
        }
    }

    public int getNodesInRing() throws RemoteException {
        return nodeList.size();
    }


    @Override
    public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.reset();
        md.update((key).getBytes());
        byte[] hashBytes = md.digest();
        BigInteger hashValue = new BigInteger(1, hashBytes);
        return Math.abs(hashValue.intValue()) % maxNodes;
    }

    public boolean isZoneFilled(int zoneID) throws RemoteException {
        int i;
        boolean is_filled = true;
        for (i = 0; i < m; i++) {
            if (nodeIds.indexOf(zoneID * m + i) < 0) {
                is_filled = false;
                break;
            }
        }
        return is_filled;
    }
}
