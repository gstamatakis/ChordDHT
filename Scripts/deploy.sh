#!/bin/bash
for (( i=1100; i<1150; i++ ))
do
	echo "Container $i is being created."
	docker run -p $i:1099 --privileged dd rmiregistry & java -jar ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost 137.27.70.106 32 &
	sleep 0.5
done

#-Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=client.policy -Djava.rmi.server.codebase=file:/app/target/classes/