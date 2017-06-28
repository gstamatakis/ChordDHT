import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class ChordNodeImpl extends UnicastRemoteObject implements ChordNode {

    public static int m = 6;
    public static final int IMAGE_STEP = 4;
    private static final int StabilizePeriod = 10000; // 10 sec
    private static final int FixFingerPeriod = 10000; // 10 sec
    private static final long serialVersionUID = 1L;
    public static int maxNodes = (int) Math.pow(2.0, (long) m);         // Maximum number of permitted nodes in the Chord Ring
    public static BootStrapNode bootstrap;
    private static int num = 0;  // used during rmi registry binding
    private static int fingerTableSize = 2 * m - 1; // finger table size
    private static int fix_finger_count = 0; // store the id of next finger entry to update
    private static Timer timerStabilize = new Timer();
    private static Timer timerFixFinger = new Timer();
    private static Logger log = null;
    public HashMap<Integer, HashMap<String, String>> data = new HashMap<>();//Data store for each Chord Node instance
    public NodeInfo node;
    public FingerTableEntry[] fingertable = null; //Data Structure to store the finger table for the Chord Node
    public NodeInfo predecessor;
    private ReentrantReadWriteLock data_rwlock = new ReentrantReadWriteLock();
    private ArrayList<HashMap<String, Result>> metrics;

    protected ChordNodeImpl(NodeInfo node) throws RemoteException {
        super();
        this.node = node;
        this.predecessor = null;
        this.fingertable = new FingerTableEntry[fingerTableSize];
        this.metrics = new ArrayList<>();
    }

    public ChordNodeImpl() throws RemoteException {
        super();
        this.node = null;
        this.predecessor = null;
        this.fingertable = new FingerTableEntry[fingerTableSize];
        this.metrics = new ArrayList<>();
    }

    /**
     * Starting point for the Chord Node instances
     *
     * @param args variable length command line argument list
     * @throws RemoteException Due to RMI.
     */
    public static void main(String[] args) throws RemoteException {
        ChordNode c;
        ChordNodeImpl cni;
        boolean running = true;
        long startTime, endTime = 0, timetaken;
        Result result = new Result();
        HashMap<String, Result> met;

        if (args.length < 2) {
            System.out.println("Usage : java ChordNodeImpl <ip address of current node> <ipaddress of bootstrap>");
            System.exit(-1);
        }

        // Logging Module initialize
        PatternLayout layout = new PatternLayout();
        String conversionPattern = "%-7p %d [%t] %c %x - %m%n";
        layout.setConversionPattern(conversionPattern);

        // creates file appender
        FileAppender fileAppender = new FileAppender();

        fileAppender.setFile("logs/chord.log");
        fileAppender.setLayout(layout);
        fileAppender.activateOptions();

        //logger assign
        log = Logger.getLogger(ChordNodeImpl.class);
        log.addAppender(fileAppender);
        log.setLevel(Level.DEBUG);

        log.info("\n## Creating chord node instance ##\n");

        String nodeIPAddress = args[0];

        try {
            startTime = System.currentTimeMillis();
            result.latency = startTime;
            String rmiUrl = "rmi://" + args[1] + "/ChordRing";
            log.debug("Contacting Bootstrap Server " + rmiUrl);
            bootstrap = (BootStrapNode) Naming.lookup(rmiUrl);
        } catch (MalformedURLException | RemoteException | NotBoundException e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }

        try {
            while (true) {
                log.trace("Checking for existing chord node instances [ChordNode_" + num + "] running on localhost");
                try {
                    c = (ChordNode) Naming.lookup("rmi://localhost/ChordNode_" + num);
                } catch (Exception e) {
                    c = null;
                }
                if (c == null) {
                    cni = new ChordNodeImpl();
                    Naming.rebind("ChordNode_" + num, cni);
                    break;
                } else {
                    num++;
                }
            }
        } catch (Exception e) {
            log.error("Error in binding ChordNode " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return;
        }

        log.info("Chord node instance created with rmi name [ChordNode_" + num + "]");

        ArrayList<NodeInfo> nodes = bootstrap.addNodeToRing(nodeIPAddress, num + "");
        if (nodes != null) {
            cni.node = nodes.get(0);
            FingerTableEntry fte = new FingerTableEntry((cni.node.nodeID + 1) % maxNodes, nodes.get(1));
            cni.fingertable[0] = fte;
            cni.predecessor = nodes.get(2);
            log.info("My ID: " + cni.node.nodeID);
            log.info("Successor ID - " + cni.fingertable[0].successor.nodeID);
            log.info("Predecessor ID - " + cni.predecessor.nodeID);
        } else {
            log.error("Join unsuccessful");
            return;
        }

        fileAppender.close();
        fileAppender = new FileAppender();
        fileAppender.setFile("logs/chord_" + cni.node.nodeID + ".log");
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

        while (running) {
            System.out.println("\nMenu: \n1. Print Finger Table"
                    + "\n2. Get Key \n3. Put Key \n4. Delete Key \n5. Display data stored \n6. Insert file \n7. Retrieve file\n8. Experiments \n9. Leave Chord Ring");
            System.out.println("Enter your choice: ");
            try {
                choice = sc.nextInt();
            } catch (Exception e) {
                System.out.println("Give valid input please.");
                continue;
            } finally {
                sc.nextLine();  // Consume newline left-over
                System.out.println("\n");
            }

            switch (choice) {
                case 1:
                    cni.print_finger_table();
                    break;
                case 2:
                    System.out.print("Enter key: ");
                    key = sc.nextLine();
                    Result getHops = new Result();
                    startTime = System.currentTimeMillis();
                    value = cni.get_value(key, getHops);
                    System.out.println(value != null ? "Value is: " + value : "Key not found.");
                    endTime = System.currentTimeMillis();
                    timetaken = endTime - startTime;
                    getHops.latency = timetaken;
                    log.info("Hop Count for get key operation: " + getHops.hopCount);
                    log.info("Time taken for get key operation: " + timetaken + "ms");
                    met = new HashMap<>();
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
                    System.out.println(res ? key + ": " + value + " successfully inserted." : "Insertion unsuccessful.");
                    endTime = System.currentTimeMillis();
                    timetaken = endTime - startTime;
                    insHops.latency = timetaken;
                    log.info("Hop Count for insert key operation: " + insHops.hopCount);
                    log.info("Time taken for insert key operation: " + timetaken + "ms");
                    met = new HashMap<>();
                    met.put("INSERT", insHops);
                    cni.metrics.add(met);
                    break;
                case 4:
                    System.out.print("Enter key: ");
                    key = sc.nextLine();
                    Result delHops = new Result();
                    startTime = System.currentTimeMillis();
                    res = cni.delete_key(key, delHops);
                    System.out.println(res ? key + " successfully deleted." : "Key not found. Deletion unsuccessful.");
                    endTime = System.currentTimeMillis();
                    timetaken = endTime - startTime;
                    delHops.latency = timetaken;
                    log.info("Hop Count for delete key operation: " + delHops.hopCount);
                    log.info("Time taken for delete key operation: " + timetaken + "ms");
                    met = new HashMap<>();
                    met.put("DELETE", delHops);
                    cni.metrics.add(met);
                    break;
                case 5:
                    System.out.println("Printing all data stored in the node");
                    cni.display_data_stored();
                    break;
                case 6:
                    try {
                        System.out.println("File name: ");
                        String resource = sc.nextLine();
                        String res2 = resource.substring(0, resource.lastIndexOf("."));
                        switch (FilenameUtils.getExtension(resource)) {
                            case "png":
                                final BufferedImage source = ImageIO.read(new File("resources/" + resource));
                                int idx = 0;
                                File dir = new File(res2 + "_parts");
                                deleteDirectory(dir);
                                if (!dir.mkdir()) {
                                    throw new Exception("Failed to create a new directory..");
                                }
                                for (int y = 0; y < source.getHeight(); y += IMAGE_STEP) {
                                    File file = new File(dir + "/" + res2 + idx++ + ".png");
                                    ImageIO.write(source.getSubimage(0, y, 128, IMAGE_STEP), "png", file);
                                    ImageCanvas imageCanvas = new ImageCanvas();
                                    imageCanvas.images.add(ImageIO.read(file));
                                    cni.insert_key(res2 + String.valueOf(idx - 1), makeString(imageCanvas), new Result());
                                }
                                deleteDirectory(new File(res2 + "_parts"));
                                break;

                            default:
                                try {
                                    String Line;
                                    BufferedReader BufferedReader = new BufferedReader(new FileReader(new File("resources/" + resource)));
                                    while ((Line = BufferedReader.readLine()) != null) {
                                        cni.insert_key(Line, "Explanation of " + Line, new Result());

                                    }
                                    BufferedReader.close();
                                } catch (Exception e) {
                                    System.out.println("Unrecognised file..");
                                }
                                break;
                        }
                    } catch (FileNotFoundException ex) {
                        System.out.println("File not found...");
                        ex.printStackTrace();
                    } catch (IIOException e) {
                        System.out.println("Unable to load file");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("Final block of loadFile exception catcher!");
                    }
                    System.out.println("File uploaded successfully..");
                    break;
                case 7:
                    try {
                        System.out.println("File name: ");
                        String resource = sc.nextLine();
                        String res3 = resource.substring(0, resource.lastIndexOf("."));
                        switch (FilenameUtils.getExtension(resource)) {
                            case "png":
                                BufferedImage finalImg = null;
                                File dir = null;
                                for (int i = 0; ; i++) {
                                    String out = cni.get_value(res3 + i, new Result());
                                    if (out == null) {
                                        break;
                                    } else if (i == 0) {
                                        dir = new File(res3 + "_out");
                                        deleteDirectory(dir);
                                        if (!dir.mkdir()) {
                                            throw new IOException("Failed to create a new directory..");
                                        }
                                    }
                                    ImageCanvas Value = (ImageCanvas) fromString(out);
                                    File file = new File(dir + "/" + res3 + i + ".png");
                                    BufferedImage image = Value.images.get(0);
                                    if (i == 0) {
                                        finalImg = new BufferedImage(128, 128, image.getType());
                                    }
                                    ImageIO.write(image, "png", file);
                                    finalImg.createGraphics().drawImage(image, 0, i * IMAGE_STEP, null);
                                    System.out.println("Image part number " + (i + 1) + " retrieved!");
                                }
                                if (finalImg != null) {
                                    File outFile = new File(res3 + ".png");

                                    deleteDirectory(outFile);
                                    deleteDirectory(new File(res3 + "_out"));

                                    ImageIO.write(finalImg, "png", outFile);
                                    System.out.println("Image retrieved successfully..");
                                } else {
                                    System.out.println("Image not found..");
                                }
                                break;

                        }
                    } catch (IOException e) {
                        System.out.println("Can not overwrite files.." +
                                "\nHave you deleted the parts_ folder?");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                case 8:
                    System.out.println("Number of keys (power of 2): ");
                    ExecutorService executorServicePUT = Executors.newFixedThreadPool(4);
                    ExecutorService executorServiceGET = Executors.newFixedThreadPool(4);

                    List<Callable<Integer>> putCallables = new ArrayList<>();
                    List<Callable<Integer>> getCallables = new ArrayList<>();

                    int numOfKeys = Integer.parseInt(sc.nextLine());
                    try (BufferedWriter bw = new BufferedWriter(new FileWriter("benchmark.txt", true))) {
                        bw.newLine();
                        bw.write("\nCount\tPut(ms)\tGet(ms)");
                        bw.newLine();
                        for (int cnt = 1; cnt <= numOfKeys; cnt *= 2) {
                            bw.write(cnt + "\t");
                            System.out.println("Count: " + cnt);

                            int finalCnt = cnt;

                            //PUT
                            startTime = System.currentTimeMillis();
                            putCallables.add(() -> {
                                for (int i = 0; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    String value1 = String.valueOf("value_t1" + i);
                                    Result result1 = new Result();
                                    cni.insert_key(key1, value1, result1);
                                }
                                return 0;
                            });

                            putCallables.add(() -> {
                                for (int i = 1; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    String value1 = String.valueOf("value_t2" + i);
                                    Result result1 = new Result();
                                    cni.insert_key(key1, value1, result1);
                                }
                                return 0;
                            });

                            putCallables.add(() -> {
                                for (int i = 2; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    String value1 = String.valueOf("value_t3" + i);
                                    Result result1 = new Result();
                                    cni.insert_key(key1, value1, result1);
                                }
                                return 0;
                            });

                            putCallables.add(() -> {
                                for (int i = 3; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    String value1 = String.valueOf("value_t4" + i);
                                    Result result1 = new Result();
                                    cni.insert_key(key1, value1, result1);
                                }
                                return 0;
                            });

                            executorServicePUT.invokeAll(putCallables);
                            endTime = System.currentTimeMillis();
                            timetaken = endTime - startTime;
                            bw.write(timetaken + "\t");

                            //GET
                            startTime = System.currentTimeMillis();
                            getCallables.add(() -> {
                                for (int i = 0; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    Result result1 = new Result();
                                    cni.get_value(key1, result1);
                                }
                                return 0;
                            });

                            getCallables.add(() -> {
                                for (int i = 1; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    Result result1 = new Result();
                                    cni.get_value(key1, result1);
                                }
                                return 0;
                            });

                            getCallables.add(() -> {
                                for (int i = 2; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    Result result1 = new Result();
                                    cni.get_value(key1, result1);
                                }
                                return 0;
                            });

                            getCallables.add(() -> {
                                for (int i = 3; i <= finalCnt; i += 4) {
                                    String key1 = String.valueOf(finalCnt * i);
                                    Result result1 = new Result();
                                    cni.get_value(key1, result1);
                                }
                                return 0;
                            });
                            executorServicePUT.invokeAll(getCallables);
                            endTime = System.currentTimeMillis();
                            timetaken = endTime - startTime;
                            bw.write(timetaken + "\n");
                        }

                        //Shutdown both executors.
                        try {
                            log.info("attempt to shutdown PUT executor");
                            executorServicePUT.shutdown();
                            executorServicePUT.awaitTermination(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            log.error("tasks interrupted");
                        } finally {
                            if (!executorServicePUT.isTerminated()) {
                                log.error("cancel non-finished tasks");
                            }
                            executorServicePUT.shutdownNow();
                            log.info("shutdown finished");
                        }

                        try {
                            log.info("attempt to shutdown GET executor");
                            executorServiceGET.shutdown();
                            executorServiceGET.awaitTermination(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            log.error("tasks interrupted");
                        } finally {
                            if (!executorServiceGET.isTerminated()) {
                                log.error("cancel non-finished tasks");
                            }
                            executorServiceGET.shutdownNow();
                            log.info("shutdown finished");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                case 9:
                    Result lhops = new Result();
                    startTime = System.currentTimeMillis();
                    if (cni.leave_ring(lhops)) {
                        timerStabilize.cancel();
                        timerFixFinger.cancel();
                        System.out.println("Node left...No more operations allowed");

                        try {
                            Naming.unbind("rmi://localhost/ChordNode_" + cni.node.port);
                            log.debug("ChordNode RMI object unbinded");
                            System.out.println("Node removed from RMI registry!");
                        } catch (Exception e) {
                            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                        }
                        running = false;
                        endTime = System.currentTimeMillis();
                        timetaken = endTime - startTime;
                        lhops.latency = timetaken;
                        log.info("Time taken for leaving the Chord network:" + timetaken + "ms");
                        sc.close();
                    } else {
                        System.out.println("Error: Cannot leave ring right now");
                    }
                    break;
            }
        }

    }

    /**
     * Read the object from Base64 string.
     *
     * @param s The serialized object.
     * @return The deserialized object.
     * @throws IOException            when decoding fails.
     * @throws ClassNotFoundException when readObj fails.
     */
    private static Object fromString(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Write the object to a Base64 string.
     *
     * @param o The object to be serialized.
     * @return The string of the serialized object.
     * @throws IOException when encoding fails.
     */
    private static String makeString(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * Right way to delete a non empty directory in Java
     *
     * @param dir The directory to be deleted (empty or not).
     * @return A boolean value indicating the success or failure of the task.
     */
    private static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i = 0; i < (children != null ? children.length : 0); i++) {
                boolean success = deleteDirectory(children[i]);
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    @Override
    public NodeInfo find_successor(int id, Result result) throws RemoteException {
        log.debug("Searching for the successor of id: " + id);
        NodeInfo newNode = find_predecessor(id, result);
        try {
            ChordNode c = (ChordNode) Naming.lookup("rmi://" + newNode.ipaddress + "/ChordNode_" + newNode.port);
            if (newNode.nodeID != this.node.nodeID) {
                result.hopCount++;
            }
            newNode = c.get_successor();
            log.debug("Successor for id: " + id + " is " + newNode.nodeID + "\n");
        } catch (Exception e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return null;
        }
        return newNode;
    }

    @Override
    public NodeInfo find_predecessor(int id, Result result) throws RemoteException {
        NodeInfo nn = this.node;
        int myID = this.node.nodeID;
        int succID = this.get_successor().nodeID;
        ChordNode c = null;


        while ((myID >= succID && (myID >= id && succID < id)) || (myID < succID && (myID >= id || succID < id))) {
            try {
                log.debug("Looking for closest preceding finger of id: " + id + " in node: " + nn.nodeID);
                if (nn == this.node) {
                    nn = closest_preceding_finger(id);
                } else {
                    if (nn.nodeID != this.node.nodeID) {
                        result.hopCount++;
                    }
                    assert c != null;
                    nn = c.closest_preceding_finger(id);
                }

                myID = nn.nodeID;
                c = (ChordNode) Naming.lookup("rmi://" + nn.ipaddress + "/ChordNode_" + nn.port);
                succID = c.get_successor().nodeID;
                if (nn.nodeID != this.node.nodeID) {
                    result.hopCount++;
                }
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                return null;
            }
        }

        return nn;
    }

    @Override
    public NodeInfo closest_preceding_finger(int id) {
        int myID = node.nodeID;
        for (int i = fingerTableSize - 1; i >= 0; i--) {
            int succID = fingertable[i].successor.nodeID;
            if ((myID < id && (succID > myID && succID < id)) || (myID >= id && (succID > myID || succID < id))) {
                return fingertable[i].successor;
            }
        }
        return this.node;
    }

	/*
    (1) Check if successor is dead (try pinging/reaching it twice).
	(2) Find the next finger table entry that does not point either to me or to the dead successor.
	(3) Once such a finger table entry is found, query that node for its predecessor.
	(3a) In a loop, follow the predecessor chain till you find the node whose predecessor is our dead successor.
	(3b) Set my successor as that node and set the predecessor of that node as me.
	(3c) Inform bootstrap to update its list of active chord nodes.
	(4) If no such finger table entry is found, contact bootstrap to return a different successor.
	*/

    public void init_finger_table(NodeInfo n, Result result) throws RemoteException {
        ChordNode c;
        try {
            c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);

            int myID = this.node.nodeID;
            for (int i = 0; i < fingerTableSize - 1; i++) {
                int nextID = fingertable[i].successor.nodeID;

                if ((myID >= nextID && (fingertable[i + 1].start >= myID || fingertable[i + 1].start <= nextID)) ||
                        (myID < nextID && (fingertable[i + 1].start >= myID && fingertable[i + 1].start <= nextID))) {

                    fingertable[i + 1].successor = fingertable[i].successor;
                } else {
                    if (n.nodeID != this.node.nodeID) {
                        result.hopCount++;
                    }
                    NodeInfo s = c.find_successor(fingertable[i + 1].start, result);

                    int myStart = fingertable[i + 1].start;
                    int succ = s.nodeID;
                    int mySucc = fingertable[i + 1].successor.nodeID;

                    if (myStart > succ) {
                        succ += maxNodes;
                    }
                    if (myStart > mySucc) {
                        mySucc += maxNodes;
                    }
                    if (myStart <= succ && succ <= mySucc) {
                        fingertable[i + 1].successor = s;
                    }
                }
                log.debug("FTE " + (i + 1) + " set as: " + fingertable[i + 1].start + " || " + fingertable[i + 1].successor.nodeID);
            }

            if (n.nodeID != this.node.nodeID) {
                result.hopCount++;
            }
            c.set_predecessor(this.node);
            log.info("predecessor of node " + n.nodeID + " set as " + this.node.nodeID);
        } catch (MalformedURLException | NotBoundException e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }
    }

    public void update_others_before_leave(Result result) throws RemoteException {
        for (int i = 1; i <= fingerTableSize; i++) {
            int id = this.node.nodeID - (int) Math.pow(2, i - 1) + 1;
            if (id < 0) {
                id += maxNodes;
            }
            NodeInfo p = find_predecessor(id, result);
            try {
                ChordNode c = (ChordNode) Naming.lookup("rmi://" + p.ipaddress + "/ChordNode_" + p.port);
                c.update_finger_table_leave(this.node, i - 1, this.get_successor(), result);
                if (this.node.nodeID != p.nodeID)
                    result.hopCount++;
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }
    }

    public void update_finger_table_leave(NodeInfo t, int i, NodeInfo s, Result result) throws RemoteException {
        if (fingertable[i].successor.nodeID == t.nodeID && t.nodeID != s.nodeID) {
            fingertable[i].successor = s;
            NodeInfo p = predecessor;

            try {
                ChordNode c = (ChordNode) Naming.lookup("rmi://" + p.ipaddress + "/ChordNode_" + p.port);
                c.update_finger_table_leave(t, i, s, result);
                if (this.node.nodeID != p.nodeID)
                    result.hopCount++;
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }
    }

    @Override
    public void send_beat() throws RemoteException {
        log.debug("Acknowledged heart beat message.");
    }

    @Override
    public void stabilize(Result result) {
        log.debug("Stabilization running on chord Node" + this.node.nodeID);

        NodeInfo successorNodeInfo = null, tempNodeInfo = null;
        ChordNode successor, temp;

        try {
            successorNodeInfo = get_successor();

            if (successorNodeInfo.nodeID == this.node.nodeID) {
                // single node so no stabilization
                successor = this;
            } else {
                log.debug("RMI CALL TO HEART BEAT:" + "rmi://" + successorNodeInfo.ipaddress + "/ChordNode_" + successorNodeInfo.port);
                successor = (ChordNode) Naming.lookup("rmi://" + successorNodeInfo.ipaddress + "/ChordNode_" + successorNodeInfo.port);
                successor.send_beat();
            }
        } catch (Exception e) {
            log.error("Failed Heart beat message. Error in stabilize: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            try {
                assert successorNodeInfo != null;
                successor = (ChordNode) Naming.lookup("rmi://" + successorNodeInfo.ipaddress + "/ChordNode_" + successorNodeInfo.port);
                successor.send_beat();
            } catch (Exception e1) {
                successor = null;
                log.error("Failed Heart beat message. Error in stabilize: " + e1.getClass() + ": " + e1.getMessage() + ": " + e1.getCause() + "\n" + Arrays.toString(e1.getStackTrace()), e1);
            }
        }
        // Current successor is dead. Get a new one.
        if (successor == null) {
            log.error("Failed to contact successor. Declare successor dead");
            // iterate over fingertable entries till you find a node that is not me and not the dead successor
            int i;
            for (i = 1; i < fingerTableSize; i++) {
                tempNodeInfo = fingertable[i].successor;
                if (tempNodeInfo.nodeID != successorNodeInfo.nodeID && tempNodeInfo.nodeID != node.nodeID)
                    break;
            }
            if (i != fingerTableSize) {
                assert tempNodeInfo != null;
                while (true) {// follow the predecessor chain from tempNodeInfo
                    try {
                        log.debug("Current node in predecessor chain " + tempNodeInfo.nodeID);
                        temp = (ChordNode) Naming.lookup("rmi://" + tempNodeInfo.ipaddress + "/ChordNode_" + tempNodeInfo.port);
                        if (temp.get_predecessor().nodeID == successorNodeInfo.nodeID) {
                            temp.set_predecessor(this.node);
                            this.set_successor(tempNodeInfo);
                            log.debug("New successor is " + tempNodeInfo.nodeID);
                            break;
                        }
                        tempNodeInfo = temp.get_predecessor();
                    } catch (Exception e) {
                        log.error("Error in stabilize while following predecessor chain: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                        break;
                    }
                }
                try {//notify the bootstrap of node exit
                    bootstrap.removeNodeFromRing(successorNodeInfo);

                } catch (RemoteException e) {
                    log.error("Error in notifying bootstrap about dead node: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            } else {
                try {// My finger table does not include a different node. Request from bootstrap for a new successor.
                    NodeInfo new_suc = bootstrap.findNewSuccessor(this.node, successorNodeInfo);
                    this.set_successor(new_suc);
                    temp = (ChordNode) Naming.lookup("rmi://" + new_suc.ipaddress + "/ChordNode_" + new_suc.port);
                    temp.set_predecessor(this.node);
                } catch (Exception e) {
                    log.error("Error in requesting new successor from bootstrap: " + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            }
        } else {// Current successor is alive. Ensure that you are your successor's predecessor.
            NodeInfo x = null;
            try {
                x = successor.get_predecessor();
                if (this.node.nodeID != successorNodeInfo.nodeID)
                    result.hopCount++;
            } catch (RemoteException e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
            if ((x != null) && (inCircularInterval(x.nodeID, this.node.nodeID, this.fingertable[0].successor.nodeID)))
                this.fingertable[0].successor = x;

            try {// Ensure that you are your successor's predecessor is set correctly.
                if (successorNodeInfo.nodeID == this.node.nodeID) {
                    successor.notify_successor(this.node);
                }
            } catch (RemoteException e) {
                log.error("Error in calling the successor's notifyall" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }
        }

    }

    @Override
    public void notify_successor(NodeInfo n) {
        if (this.predecessor == null)
            this.predecessor = n;
        if (inCircularInterval(n.nodeID, this.predecessor.nodeID, this.node.nodeID))
            this.predecessor = n;

    }

    private boolean inCircularInterval(int x, int a, int b) {
        boolean val = false;
        if (a == b)
            val = true;
        else if (a < b) {// normal range
            if ((x > a) && (x < b))
                val = true;
        } else { // when on one current node is after 0 but predecessor is before 0
            if ((x > a) && (x < (b + maxNodes))) {// in ring before 0
                val = true;
            } else if ((x < b) && ((x + maxNodes) > a)) {// in ring after 0
                val = true;
            }
        }
        return val;
    }

    private boolean inCircularIntervalEndInclude(int x, int a, int b) {
        return (x == b) || inCircularInterval(x, a, b);
    }

    @Override
    public void fix_fingers(Result result) throws RemoteException {
        log.debug("Fix_fingers running on chord Node " + node.nodeID);
        //periodically fix all fingers
        fix_finger_count = fix_finger_count + 1;
        if (fix_finger_count == fingerTableSize) {
            fix_finger_count = 1;
        }
        log.debug("Running fix_finger with i: " + fix_finger_count);
        fingertable[fix_finger_count].successor = find_successor(fingertable[fix_finger_count].start, result);
    }

    /*
     * This function is called after contacting the BootStrap server and obtaining the successor and predecessor nodes to initialize finger table and update other nodes after joining.
     *
     * @param result Result object to assist in metrics collection
     * @return null
     */
    public void run(final Result result) {
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
        for (i = 1; i < m; i++) {
            int start = (this.node.nodeID + (int) Math.pow(2, i)) % maxNodes;
            NodeInfo succ = this.node;
            FingerTableEntry fte = new FingerTableEntry(start, succ);
            fingertable[i] = fte;
        }
        for (j = m - 2; i < fingerTableSize; i++, j--) {
            int start = (this.node.nodeID + maxNodes - (int) Math.pow(2, j)) % maxNodes;
            NodeInfo succ = this.node;
            FingerTableEntry fte = new FingerTableEntry(start, succ);
            fingertable[i] = fte;
        }

		/* Order of operations for initializing a new node:
        1) Initialize Finger table.
		2) Inform successor node.
		3) Migrate keys from successor.
		4) Inform predecessor node.
		*/

        if (ringsize > 1) { //More than one node in the Chord Ring

            log.info("Starting finger table initialization.\n");
            try {
                this.init_finger_table(suc, result);
            } catch (RemoteException e) {
                log.error(e);
            }

            log.info("Starting key migration");
            try {
                c = (ChordNode) Naming.lookup("rmi://" + suc.ipaddress + "/ChordNode_" + suc.port);
                c.migrate_keys(this.predecessor, this.node, result);
                if (this.node.nodeID != suc.nodeID)
                    result.hopCount++;
            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            }

            try {// set successor of predecessor as me
                c = (ChordNode) Naming.lookup("rmi://" + predecessor.ipaddress + "/ChordNode_" + predecessor.port);
                c.set_successor(this.node);
                log.info("successor of node " + predecessor.nodeID + " set as " + this.node.nodeID);

                if (predecessor.nodeID != this.node.nodeID)
                    result.hopCount++;

            } catch (Exception e) {
                log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
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
            HashMap<String, Result> m = new HashMap<>();
            m.put("JOIN", result);
            this.metrics.add(m);
        } catch (Exception e) {
            log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
        }


        //Set the timer to run notify every StabilizePeriod
        timerStabilize.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                stabilize(result);
            }
        }, new Date(System.currentTimeMillis()), ChordNodeImpl.StabilizePeriod);

        //Set the timer to run notify every FixFingerPeriod
        timerFixFinger.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                try {
                    fix_fingers(result);
                } catch (Exception e) {
                    log.error(e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
                }
            }
        }, new Date(System.currentTimeMillis()), ChordNodeImpl.FixFingerPeriod);
    }

    public NodeInfo get_predecessor() throws RemoteException {
        return this.predecessor;
    }

    @Override
    public void set_predecessor(NodeInfo p) throws RemoteException {
        this.predecessor = p;
    }

    @Override
    public NodeInfo get_successor() throws RemoteException {
        return this.fingertable[0].successor;
    }

    @Override
    public void set_successor(NodeInfo n) throws RemoteException {
        this.fingertable[0].successor = n;
    }

    @Override
    public void print_finger_table() throws RemoteException {
        System.out.println("My ID: " + node.nodeID + " Predecessor ID: " + (predecessor == null ? "NULL" : predecessor.nodeID));
        System.out.println("Index\tStart\tSuccessor ID\tIP Address\tRMI Identifier");
        for (int i = 0; i < fingerTableSize; i++) {
            System.out.println((i + 1) + "\t" + fingertable[i].start + "\t" + fingertable[i].successor.nodeID + "\t\t" + fingertable[i].successor.ipaddress + "\t" + fingertable[i].successor.port);
        }
    }

    @Override
    public boolean insert_key(String key, String value, Result result) {
        try {
            long endTime, startTime, timetaken;
            startTime = System.currentTimeMillis();
            int keyID = generate_ID(key, maxNodes);
            log.info("Inserting keyID " + keyID + " for key " + key + " with value " + value);
            NodeInfo n = find_successor(keyID, result);
            if (n != this.node) {
                ChordNode c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
                result.hopCount++;
                boolean flag = c.insert_key_local(keyID, key, value, result);
                endTime = System.currentTimeMillis();
                timetaken = endTime - startTime;
                result.latency = timetaken;
                log.info("Hop Count for insert key operation: " + result.hopCount);
                log.info("Time taken for insert key operation: " + timetaken + "ms");
                return flag;
            } else {
                boolean flag = insert_key_local(keyID, key, value, result);
                endTime = System.currentTimeMillis();
                timetaken = endTime - startTime;
                result.latency = timetaken;
                log.info("Hop Count for insert key operation: " + result.hopCount);
                log.info("Time taken for insert key operation: " + timetaken + "ms");
                return flag;
            }
        } catch (Exception e) {
            log.error("Error in inserting keys" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return false;
        }
    }

    @Override
    public boolean delete_key(String key, Result result) {
        try {
            int keyID = generate_ID(key, maxNodes);
            log.info("Deleting key :" + key + "with key hash" + keyID);
            NodeInfo n = find_successor(keyID, result);
            if (n != this.node) {
                ChordNode c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
                result.hopCount++;
                return c.delete_key_local(keyID, key, result);
            } else {
                return delete_key_local(keyID, key, result);
            }
        } catch (Exception e) {
            log.error("Error in deleting key" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return false;
        }
    }

    @Override
    public String get_value(String key, Result result) {
        try {
            long endTime, startTime, timetaken;
            startTime = System.currentTimeMillis();
            int keyID = generate_ID(key, maxNodes);
            NodeInfo n = find_successor(keyID, result);
            if (n != this.node) {
                ChordNode c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
                result.hopCount++;
                String val = c.get_key_local(keyID, key, result);
                endTime = System.currentTimeMillis();
                timetaken = endTime - startTime;
                log.info("Time taken for query key operation: " + timetaken + "ms");
                return val;
            } else {
                String val = get_key_local(keyID, key, result);
                endTime = System.currentTimeMillis();
                timetaken = endTime - startTime;
                log.info("Time taken for query key operation: " + timetaken + "ms");
                return val;
            }
        } catch (Exception e) {
            log.error("Error in get value of key" + e.getClass() + ": " + e.getMessage() + ": " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()), e);
            return null;
        }
    }

    @Override
    public boolean insert_key_local(int keyID, String key, String value, Result result) throws RemoteException {
        boolean res = true;

        data_rwlock.writeLock().lock();

        if (!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
            data_rwlock.writeLock().unlock();
            res = insert_key(key, value, result);
        } else {
            HashMap<String, String> entry = data.get(keyID);
            if (entry == null) {
                entry = new HashMap<>();
                data.put(keyID, entry);
            }
            entry.put(key, value);
            data_rwlock.writeLock().unlock();
            log.info("Inserted key - " + key + " with value - " + value);
        }
        return res;
    }

    @Override
    public boolean delete_key_local(int keyID, String key, Result result) throws RemoteException {
        boolean res = true;
        data_rwlock.writeLock().lock();
        if (!inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
            data_rwlock.writeLock().unlock();
            res = delete_key(key, result);
        } else {
            HashMap<String, String> entry = data.get(keyID);
            if (entry != null)
                if (entry.get(key) != null) {
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
    public String get_key_local(int keyID, String key, Result result) throws RemoteException {
        String val = null;

        data_rwlock.readLock().lock();

        HashMap<String, String> entry = data.get(keyID);
        if (entry != null) {
            val = entry.get(key);
        }

        data_rwlock.readLock().unlock();
        if (entry == null && !inCircularIntervalEndInclude(keyID, get_predecessor().nodeID, node.nodeID)) {
            val = get_value(key, result);
        }
        return val;
    }

    @Override
    public boolean leave_ring(Result result) throws RemoteException {//TODO needs more testing, although it is successful 9/10 times.
        ChordNode c;
        data_rwlock.writeLock().lock();

        try {
            c = (ChordNode) Naming.lookup("rmi://" + this.get_successor().ipaddress + "/ChordNode_" + this.get_successor().port);

            for (Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()) {
                int key = hashkeys.getKey();
                for (Map.Entry<String, String> e : hashkeys.getValue().entrySet()) {
                    log.info("Migrating - HashKey: " + key + "\t Key: " + e.getKey() + "\t Value: " + e.getValue());
                    c.insert_key_local(key, e.getKey(), e.getValue(), result);
                    if (this.node.nodeID != this.get_successor().nodeID)
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
            if (this.node.nodeID != this.get_successor().nodeID) {
                result.hopCount++;
            }
            c.set_predecessor(this.get_predecessor());

            //Set predecessor's successor to my successor
            c = (ChordNode) Naming.lookup("rmi://" + this.predecessor.ipaddress + "/ChordNode_" + this.predecessor.port);
            if (this.node.nodeID != this.get_successor().nodeID) {
                result.hopCount++;
            }
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
    public int generate_ID(String key, int maxNodes) throws RemoteException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");//TODO switch to a better algo if needed. Although its "good enough"
        md.reset();
        byte[] hashBytes = md.digest(key.getBytes());
        BigInteger hashValue = new BigInteger(1, hashBytes);
        log.debug("key:" + key + " hash:" + Math.abs(hashValue.intValue()) % maxNodes);
        return Math.abs(hashValue.intValue()) % maxNodes;
    }

    @Override
    public void migrate_keys(NodeInfo pred, NodeInfo newNode, Result result) throws RemoteException {
        ArrayList<Integer> removelist = new ArrayList<>();

        data_rwlock.writeLock().lock();

        for (Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            if (this.inCircularIntervalEndInclude(key, pred.nodeID, newNode.nodeID)) {
                for (Map.Entry<String, String> e : hashkeys.getValue().entrySet()) {
                    try {
                        ChordNode c = (ChordNode) Naming.lookup("rmi://" + newNode.ipaddress + "/ChordNode_" + newNode.port);
                        c.insert_key_local(key, e.getKey(), e.getValue(), result);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
                removelist.add(key);
            }
        }
        for (Integer i : removelist) {
            data.remove(i);
        }
        data_rwlock.writeLock().unlock();
    }

    @Override
    public void display_data_stored() throws RemoteException {
        for (Map.Entry<Integer, HashMap<String, String>> hashkeys : data.entrySet()) {
            int key = hashkeys.getKey();
            for (Map.Entry<String, String> e : hashkeys.getValue().entrySet()) {
                System.out.print("Hash Key: " + key);
                System.out.print("\tActual Key: " + e.getKey());
                System.out.println("\tActual Value: " + e.getValue());
            }
        }
    }

    @Override
    public void makeCall(NodeInfo n) throws RemoteException {
        if (n != null) {
            ChordNode c;
            try {
                c = (ChordNode) Naming.lookup("rmi://" + n.ipaddress + "/ChordNode_" + n.port);
                c.send_beat();
            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }
}
