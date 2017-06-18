#!/usr/bin/env bash
###BootStrap Node
java -Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=security.policy -Djava.rmi.server.codebase=file:/home/gstamatakis/Projects/ChordDHT/target/classes/ -jar JARs/BootStrapNode.jar

###Client/Chord Node
java -Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=security.policy -Djava.rmi.server.codebase=file:/home/gstamatakis/Projects/ChordDHT/target/classes/ -jar JARs/ChordNode.jar localhost 147.27.70.106 5