import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

/**
 * Created  by gstamatakis on 26-Jun-17.
 */
public class ChordNodeImplTest {
    private BootStrapNode bootstrap;
    private int num = 0;
    private int maxNodes = 32;

    /**
     * Tests insertion of a key-value pair in the ring.
     *
     * @throws RemoteException In case of an RMI exception.
     */

    @Test
    void insert_key() throws RemoteException {
        ChordNode c;
        ChordNodeImpl cni;
        long startTime, endTime, timetaken;
        Result result = new Result();

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

        String nodeIPAddress = "localhost";
        int zoneID = Integer.parseInt("5");

        try {
            startTime = System.currentTimeMillis();
            result.latency = startTime;
            String rmiUrl = "rmi://" + "147.27.70.106" + "/ChordRing";
            bootstrap = (BootStrapNode) Naming.lookup(rmiUrl);
        } catch (MalformedURLException | RemoteException | NotBoundException e) {
            e.printStackTrace();
            Assert.fail();
        }

        try {
            while (true) {
                try {
                    c = (ChordNode) Naming.lookup("rmi://localhost/ChordNode_" + num);
                } catch (NotBoundException e) {
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
            e.printStackTrace();
            Assert.fail();
            return;
        }

        ArrayList<NodeInfo> nodes = bootstrap.addNodeToRing(nodeIPAddress, num + "", zoneID);
        if (nodes != null) {
            cni.node = nodes.get(0);
            FingerTableEntry fte = new FingerTableEntry((cni.node.nodeID + 1) % maxNodes, nodes.get(1));
            cni.fingertable[0] = fte;
            cni.predecessor = nodes.get(2);
        } else {
            return;
        }

        fileAppender.close();
        fileAppender = new FileAppender();
        fileAppender.setFile("logs/chord_" + cni.node.nodeID + ".log");
        fileAppender.setLayout(layout);
        fileAppender.setAppend(false);
        fileAppender.activateOptions();

        cni.bootstrap = this.bootstrap;
        cni.run(result);

        String key, value;
        boolean res;

        for (int i = 0; i < 10; i++) {
            System.out.println("Inserting key: " + i);
            key = String.valueOf(i);
            System.out.println("Inserting value: value" + i);
            value = "value" + i;
            Result insHops = new Result();
            startTime = System.currentTimeMillis();
            res = cni.insert_key(key, value, insHops);
            if (res)
                System.out.println(key + ": " + value + " successfully inserted.");
            else {
                Assert.fail("Insertion unsuccessful!");
            }
            endTime = System.currentTimeMillis();
            timetaken = endTime - startTime;
            System.out.println("Time taken for i`th insertion: " + timetaken);
        }
    }

    @Test
    void delete_key() {
        Assert.fail();
    }

    @Test
    void get_value() {
        Assert.fail();
    }

}