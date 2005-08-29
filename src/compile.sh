#!/bin/sh

# Icky compile script but it'll work. For now.

# Check for updates of HorriblePerlScript!
if [ src/HorriblePerlScript.pl -nt org/uwcs/choob/Choob.java ]; then
	echo Rebuilding event system...
	perl src/HorriblePerlScript.pl
fi

javac -classpath lib/bsh-2.0b4.jar:lib/mysql-connector-java-3.1.8-bin.jar:lib/pircbot.jar org/uwcs/choob/support/events/*.java org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java
