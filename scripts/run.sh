#!/usr/bin/env bash
###BootStrap Node
java -Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=security.policy -Djava.rmi.server.codebase=file:/home/gstamatakis/Projects/ChordDHT/target/classes/ -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar
java  -Djava.rmi.server.hostname=147.27.70.106 -jar target/BootStrapNode.jar

###Client/Chord Node
java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 147.27.70.106 5
java -Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=security.policy -Djava.rmi.server.codebase=file:/home/gstamatakis/Projects/ChordDHT/target/classes/ -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 147.27.70.106 5
