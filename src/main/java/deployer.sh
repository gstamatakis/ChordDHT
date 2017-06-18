#!/bin/bash

##################################################
# Script to deploy chord nodes on multiple machines
##################################################

# Bootstrap machine's ip address
bootstrapip="Current Node IP Address"

# variable 'machine' is an array storing ip addresses of each node 
machines=("Current Node IP Address" "Current Node IP Address" "Current Node IP Address")

testkeystoinsert=999
testkeystoquery=333

#cleanup
rm *.class
rm *.log
rm chordx2.tar.gz
pkill rmiregistry
pkill rmiregistry
pkill rmiregistry
pkill java
pkill java
pkill java
pkill java

machineCount=0

#kill all rmi and java process on each node
for i in "${machines[@]}"
do
echo "Cleaning java and rmi process on node ["$i"]"
ssh root@$i  "pkill rmiregistry"
ssh root@$i  "pkill rmiregistry"
ssh root@$i  "pkill rmiregistry"
ssh root@$i  "pkill java"
ssh root@$i  "pkill java"
ssh root@$i  "pkill java"
ssh root@$i  "pkill java"
ssh root@$i  "rm -rf chordx2.tar.gz"
machineCount=$((machineCount+1))
done

#create a tar file of source
tar -zcvf chordx2.tar.gz *.java *.jar

#compile and run the bootstrap server
javac -cp .:log4j-1.2.17.jar *.java
rmiregistry &
java -cp .:log4j-1.2.17.jar BootStrapNodeImpl $machineCount $testkeystoinsert $testkeystoquery &

# using private key on current node, run the chordnode on other machines
for i in "${machines[@]}"
do
echo -e "Copying files to machine ["$i"]\n"
scp chordx2.tar.gz root@$i:/root/chordx2.tar.gz > /dev/null 2>&1
ssh root@$i  "iptables -F"
ssh root@$i  "rm -rf /root/chordx2> /dev/null 2>&1"
ssh root@$i  "pkill rmiregistry"
ssh root@$i  "pkill java"
ssh root@$i  "pkill java"
ssh root@$i  "mkdir /root/chordx2"
ssh root@$i  "tar -zxvf chordx2.tar.gz -C /root/chordx2 > /dev/null 2>&1"
ssh root@$i  "echo 'cd /root/chordx2'>run.sh"
ssh root@$i  "echo 'javac -cp .:log4j-1.2.17.jar *.java </dev/null >/var/log/root-backup.log 2>&1'>>run.sh &"
ssh root@$i  "echo 'rmiregistry </dev/null >/var/log/root-backup.log 2>&1 &'>>run.sh"
ssh root@$i  "echo 'java -cp .:log4j-1.2.17.jar ChordNodeImpl $i $bootstrapip -1 </dev/null >/var/log/root-backup.log 2>&1 &'>>run.sh"
ssh root@$i  "chmod +777 run.sh"
echo -e "Running Chord Node instance on machine ["$i"]\n"
ssh root@$i  "./run.sh"
done
