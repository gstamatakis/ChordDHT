import org.junit.Assert;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;


class ChordNodeImplTest {
    /**
     * Tests insertion of a key-value pair in the ring.
     *
     * @throws RemoteException In case of an RMI exception.
     */
    @org.junit.jupiter.api.Test
    void insert_key() throws RemoteException {
        BootStrapNode bootstrap = null;
        ChordNode c;
        ChordNodeImpl cni;
        int num = 0;
        long startTime;
        Result result = new Result();
        int maxNodes = 32; // maxNodes = 2^m

        String nodeIPAddress = "localhost";
        int zoneID = Integer.parseInt("5");

        try {
            startTime = System.currentTimeMillis();
            result.latency = startTime;
            String rmiUrl = "rmi://" + "147.27.70.106" + "/ChordRing";
            bootstrap = (BootStrapNode) Naming.lookup(rmiUrl);
        } catch (MalformedURLException | RemoteException | NotBoundException e) {
            Assert.fail(e.toString());
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

        Assert.assertNotNull(bootstrap);

        ArrayList<NodeInfo> nodes = bootstrap.addNodeToRing(nodeIPAddress, num + "", zoneID);
        if (nodes != null) {
            cni.node = nodes.get(0);
            FingerTableEntry fte = new FingerTableEntry((cni.node.nodeID + 1) % maxNodes, nodes.get(1));
            cni.fingertable[0] = fte;
            cni.predecessor = nodes.get(2);
        } else {
            Assert.fail();
            return;
        }

        cni.run(result);

        for (int i = 0; i < 1000; i++) {
            String key = String.valueOf(i);
            String value = "Value " + i;
            Result insHops = new Result();
            boolean res = cni.insert_key(key, value, insHops);
            if (!res) {
                Assert.fail("Unsuccessful insertion!");
            }
        }
    }

    @org.junit.jupiter.api.Test
    void delete_key() {
        Assert.fail();
    }

    @org.junit.jupiter.api.Test
    void get_value() {
        Assert.fail();
    }

}