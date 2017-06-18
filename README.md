# CHORDX
Chord is a protocol and algorithm used in distributed and peer to peer environment to locate node with m-bit identifier. Chord does not require any centralized server to locate node containing the resources. Nodes are arranged in a ring type order using consistent hashing and nodes can join and depart anytime.

ChordX is an improved version of chord which aims at improving handling certain drawbacks of the original chord implementation and provide reduced latency and number of messages exchanges between nodes.

## Environment Requirements
The ChordX algorithm is implementation in JAVA language which offer cross platform support. Below is the recommended requirement to run a stable and efficient chordX implementation with 100,000 keys:
1. 1 x86-64 CPU core
2. 2 GB RAM 
3. 1 Mbps network interface
4. 10 GB Disk space
5. Operation System : Linux or Windows
6. JDK and JRE version 8

## Version
The original private git repo contains 2 version for chord implementation:
1. Original Chord Implementation : Branch `master`
2. Improved ChordX Implementation : Branch `improvements`

Original private repo link - https://github.ncsu.edu/jjawaha/chordX

Note: This git repo has only the improvements

## Setup
ChordX can be run in 2 ways, Manually and Automated. 

### To run manually, follow the below steps:
1. Clone the project on local machine by running `git clone https://github.com/jerisalan/chordX.git`
2. Compile all the java files using `javac -cp .:log4j-1.2.17.jar *.java`
3. Run the RMI Registry to enable remote procedure calls by using the command 
* Linux : `rmiregistry &`
* Windows : `start rmiregistry`
4. Run the Bootstrap Node by running 
* Linux : `java -cp .:log4j-1.2.17.jar BootStrapNodeImpl`
* Windows : `java -cp .;log4j-1.2.17.jar BootStrapNodeImpl`
5. If running the ChordX instance on machine, run the step 2 and 3 again
6. Run the original ChordX instance by running
* Linux : `java -cp .:log4j-1.2.17.jar ChordNodeImpl <IP ADDRESS OF CURRENT NODE> <IP ADDRESS OF BOOTSTRAP NODE>`
* Windows : `java -cp .;log4j-1.2.17.jar ChordNodeImpl <IP ADDRESS OF CURRENT NODE> <IP ADDRESS OF BOOTSTRAP NODE>`

**Note:** To run improved chordX node instances, switch to improvements branch and follow same steps till 5 and run the below commands to start chordX instances where additional Zone ID parameter is specified to allow node join a paticular zone. Zone ID can range from 0 to m-1 values for m-bit identifier of Chord ring [default value of m is 5, but can be configure by modifying variable "m" and "maxNodes" in  "src/BootStrapNodeImpl.java" & variable "ftsize" and "maxNodes" in "src/ChordNodeImpl.java"]. If Zone ID is not known, provide -1 as the value.
* Linux : `java -cp .:log4j-1.2.17.jar ChordNodeImpl <IP ADDRESS OF CURRENT NODE> <IP ADDRESS OF BOOTSTRAP NODE> <ZONE ID>`
* Windows : `java -cp .;log4j-1.2.17.jar ChordNodeImpl <IP ADDRESS OF CURRENT NODE> <IP ADDRESS OF BOOTSTRAP NODE> <ZONE ID>`

### To run automatically on Linux, follow the below steps:
1. Clone the project on local machine by running `git clone https://github.com/jerisalan/chordX.git`
2. Configure the key based authentication on each node where chordX instance will be running and store the private key on a single node called Deployer. Deployer should also run the Bootstrap server
3. Configure the firewall on each node to allow machines to communicate on port 1099
4. In the deployer `deployer.sh` , configure the IP Address of bootstrap Node (i.e. current node) and all the machines where chordX will be running. Further, to automate the testing process, enter the keys to insert and query in variables "testkeystoinsert" and "testkeystoquery" inside the deployer.sh
5. Modify the permission of `deployer.sh` to run as executable and run the command './deploer.sh'
6. All the testing will be performed automatically and performance metrics will be displayed on screen
