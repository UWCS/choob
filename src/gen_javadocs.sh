#!/bin/sh

# Generates javadocs for the project and puts them in ../docs/javadocs

echo "Use or port the windows version, shown in this file."

#javadoc -notimestamp -classpath .;lib/js-rhino-1.6r2.jar;lib/jazzy-core.jar;lib/pircbot.jar;lib/bsh-2.0b4.jar -d ../docs/javadocs/ org.uwcs.choob org.uwcs.choob.support org.uwcs.choob.support.events org.uwcs.choob.modules org.uwcs.choob.plugins