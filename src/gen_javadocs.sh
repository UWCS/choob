#!/bin/sh

# Generates javadocs for the project and puts them in ../docs/javadocs

echo "Use or port the windows version, shown in this file."

#javadoc -notimestamp -classpath .;lib/c3p0-0.9.0.2.jar;lib/js-rhino-1.6r2.jar;lib/jazzy-core.jar;lib/pircbot.jar;lib/bsh-2.0b4.jar -d ../docs/javadocs/ uk.co.uwcs.choob uk.co.uwcs.choob.support uk.co.uwcs.choob.support.events uk.co.uwcs.choob.modules uk.co.uwcs.choob.plugins