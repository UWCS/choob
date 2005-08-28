#!/bin/sh

# Icky compile script but it'll work. For now.

# Check for updates of HorriblePerlScript!
if [ HorriblePerlScript.pl -nt org/uwcs/choob/Choob.java ]; then
	echo Rebuilding event system...
	perl HorriblePerlScript.pl
fi

javac -classpath beanshell/bsh-2.0b4.jar:database/mysql-connector-java-3.1.8-bin.jar:pircbot/pircbot.jar org/uwcs/choob/support/events/*.java org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java
