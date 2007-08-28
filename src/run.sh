#!/bin/sh

#####
# PLEASE UPDATE run.bat when you change this file (at least add a "REM FIXME")
#####

RUN=1
while [ $RUN != 0 ]; do
    java -cp .:lib/c3p0-0.9.1.2.jar:lib/jcfd.jar:lib/jazzy-core.jar:lib/bsh-2.0b4.jar:lib/mysql-connector-java-5.0.6-bin.jar:lib/pircbot.jar:lib/js-rhino-1.6r2.jar uk.co.uwcs.choob.ChoobMain
    RUN=$?
    sleep 15
done
