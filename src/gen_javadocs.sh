#!/bin/sh

# Generates javadocs for the project and puts them in ../docs/javadocs

javadoc -d ../docs/javadocs/ -private org.uwcs.choob org.uwcs.choob.support org.uwcs.choob.modules org.uwcs.choob.plugins
