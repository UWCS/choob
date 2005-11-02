#!/bin/sh

# Icky compile script but it'll work. For now.

# Check for updates of HorriblePerlScript!
if [ misc/HorriblePerlScript.pl -nt org/uwcs/choob/Choob.java ]; then
	echo Rebuilding event system...
	perl misc/HorriblePerlScript.pl
fi

javac -classpath lib/c3p0-0.9.0.2.jar:lib/jcfd.jar:lib/jazzy-core.jar:lib/bsh-2.0b4.jar:lib/mysql-connector-java-3.1.10-bin.jar:lib/pircbot.jar:lib/js-rhino-1.6r2.jar org/uwcs/choob/support/events/*.java org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java
