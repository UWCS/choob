#!/bin/sh

#####
# PLEASE UPDATE run.bat when you change this file (at least add a "REM FIXME")
#####

RUN=1
while [ $RUN != 0 ]; do
    java -cp .:lib/junit-4.5.jar:lib/c3p0-0.9.1.2.jar:lib/jcfd.jar:lib/jazzy-core.jar:lib/mysql-connector-java-5.1.5-bin.jar:lib/pircbot.jar:lib/js-rhino-1.6r2.jar:lib/jersey.jar:lib/jsr311-api.jar:lib/asm-3.1.jar uk.co.uwcs.choob.ChoobMain
    RUN=$?
    sleep 15
done
