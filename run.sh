#!/usr/bin/env bash
java -Djava.rmi.server.useCodebaseOnly=false -Djava.security.policy=security.policy -Djava.rmi.server.codebase=file:/home/gstamatakis/Projects/ChordDHT/target/classes/ -jar target/ChordDHT-1.0-SNAPSHOT-jar-with-dependencies.jar
