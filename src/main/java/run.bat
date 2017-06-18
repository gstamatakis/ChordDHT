cls
cd C:\Users\alanj\Documents\Projects\chordX\src\
C:
javac -cp ".;log4j-1.2.17.jar" -g *.java
start rmiregistry
start java -cp ".;log4j-1.2.17.jar" BootStrapNodeImpl
echo Starting BOOTSTRAP
timeout 5
pause
start java -cp ".;log4j-1.2.17.jar" ChordNodeImpl 127.0.0.1 127.0.0.1 -1
echo Starting BOOTSTRAP Node 1
timeout 5
pause
start java -cp ".;log4j-1.2.17.jar" ChordNodeImpl 127.0.0.1 127.0.0.1 -1
echo Starting BOOTSTRAP Node 2
pause
