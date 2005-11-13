#!/bin/sh

# Icky compile script but it'll work. For now.

# Check for updates of HorriblePerlScript!
if [ misc/HorriblePerlScript.java -nt org/uwcs/choob/Choob.java ]; then
	echo Rebuilding event system...
	if ! javac -d misc misc/HorriblePerlScript.java; then
		echo "Failed to compile events generator."
		exit 1
	fi
	if ! java -cp misc org.uwcs.choob.misc.HorriblePerlScript; then
		echo "Failed to run events generator."
		exit 1
	fi
fi

if [ org/uwcs/choob/support/ObjectDbClauseParser.jjt -nt org/uwcs/choob/support/ObjectDbClauseParser.java ]; then
	echo Recompiling ObjectDB parser...
	if ! jjtree -OUTPUT_DIRECTORY=org/uwcs/choob/support org/uwcs/choob/support/ObjectDbClauseParser.jjt; then
		echo "Failed to JJT-compile the ObjectDB Clause Parser."
		exit 1
	fi
	if ! javacc -OUTPUT_DIRECTORY=org/uwcs/choob/support org/uwcs/choob/support/ObjectDbClauseParser.jj; then
		echo "Failed to JJ-compile the ObjectDB Clause Parser."
		exit 1
	fi
fi

javac -classpath lib/c3p0-0.9.0.2.jar:lib/jcfd.jar:lib/jazzy-core.jar:lib/bsh-2.0b4.jar:lib/mysql-connector-java-3.1.10-bin.jar:lib/pircbot.jar:lib/js-rhino-1.6r2.jar org/uwcs/choob/support/events/*.java org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java
