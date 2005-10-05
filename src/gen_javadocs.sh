#!/bin/sh

# Generates javadocs for the project and puts them in ../docs/javadocs

javadoc -d ../docs/javadocs/ -private org.uwcs.choob org.uwcs.choob.support org.uwcs.choob.modules org.uwcs.choob.plugins
#javadoc -classpath .;lib/jazzy-core.jar;lib/pircbot.jar;lib/bsh-2.0b4.jar -d ../docs/javadocs/ -private org.uwcs.choob org.uwcs.choob.support org.uwcs.choob.support.events org.uwcs.choob.modules org.uwcs.choob.plugins