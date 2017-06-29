###Author 
Giorgos Stamatakis

##Summary
This is a distributed dictionary that can save Key-Value pairs in a network of nodes.
The network implements a Chord-like logic using java RMI for RPC. Maven is used to build the fat 
JARs required in order to run the project. The project consists of 2 JARs that need to be built, the 
BootStrapNode JAR and the ChordNode JAR both of which can be built by changing the MainClass in 
pom.xml (line 24) to either ChordNodeImpl or BootStrapNodeImpl. At any given time a BootStrap node 
must be online with a known IP as all the Client nodes (ChordNodes) will need to contact the bootstrap
node in order to join the network, the bootstrap node servers as a well known point of entry in the
network that handles the initial join of ever other node.

## Instructions
1. Change the pom.xlm MainClass (line 24) as mentioned in the summary.
2. Build the maven project (or use the precompiled JARs) by running:


        mvn clean compile assembly:single

3a. To setup the BootStrap Node run rmiregistry command in the target/classes folder of the bootstrap built mvn project 
and then execute the bootstrap jar.

        rmiregistry &
        
        java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar
        
3b. To setup the Client Node simply run (followed by example):
        
        java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar [local IP] [BootStrap Node IP]
        
        java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 147.27.70.106       

4 . If done correctly a log would have spawned in the logs directory with additional info.

5 . Use the CLI for more options.


##Links
1. Chord Paper: http://cs.brown.edu/courses/csci2950-g/papers/chord.pdf
2. Open Chord site: http://open-chord.sourceforge.net/
3. Java RMI documentation: http://docs.oracle.com/javase/7/docs/technotes/guides/rmi/
4. Docker documentation: https://docs.docker.com/engine/installation/linux/ubuntu/#install-docker
5. Maven documentation: http://maven.apache.org/guides/
6. IntelliJ IDEA Website: https://www.jetbrains.com/idea/specials/idea/idea.html
