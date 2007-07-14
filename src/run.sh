#!/bin/sh

#####
# PLEASE UPDATE run.bat when you change this file (at least add a "REM FIXME")
#####

RUN=1
while [ $RUN != 0 ]; do
    java -cp .:bsh-2.0b4.jar:c3p0-0.9.1.2.jar:en_phonet.dat:jazzy-core.jar:jcfd.jar:js-rhino-1.6r2.jar:mysql-connector-java-5.0.6-bin.jar:pircbot.jar uk.co.uwcs.choob.ChoobMain
    RUN=$?
    sleep 15
done
