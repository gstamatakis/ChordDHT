#!/bin/bash
for (( i=0; i<50; i++ ))
do
    java -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar localhost localhost &
    sleep 0.5
done