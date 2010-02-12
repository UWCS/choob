#!/bin/sh

#####
# PLEASE UPDATE run.bat when you change this file (at least add a "REM FIXME")
#####

RUN=1
while [ $RUN != 0 ]; do
    java -cp .:lib/* uk.co.uwcs.choob.ChoobMain
    RUN=$?
    sleep 15
done
