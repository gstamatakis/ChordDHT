#!/bin/bash
for (( i=1100; i<1150; i++ ))
do
#	echo "Container $i is being created."
#	docker run dd java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost localhost &
#	sleep 0.5
    java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost localhost &
    sleep 0.5
done