#!/bin/sh

#####
# PLEASE UPDATE compile.bat when you change this file (at least add a "REM FIXME")
#####

# Icky compile script but it'll work. For now.

# Check for updates of HorriblePerlScript!
if [ misc/HorriblePerlScript.java -nt uk/co/uwcs/choob/Choob.java ]; then
	echo Rebuilding event system...
	if ! javac -J-Xmx64m -d misc misc/HorriblePerlScript.java; then
		echo "Failed to compile events generator."
		exit 1
	fi
	if ! java -Xmx64m -cp misc uk.co.uwcs.choob.misc.HorriblePerlScript; then
		echo "Failed to run events generator."
		exit 1
	fi
fi

if [ uk/co/uwcs/choob/support/ObjectDBClauseParser.jjt -nt uk/co/uwcs/choob/support/ObjectDBClauseParser.java ]; then
	echo Recompiling ObjectDB parser...
	if ! jjtree -OUTPUT_DIRECTORY=uk/co/uwcs/choob/support uk/co/uwcs/choob/support/ObjectDBClauseParser.jjt; then
		echo "Failed to JJT-compile the ObjectDB Clause Parser."
		exit 1
	fi
	if ! javacc -OUTPUT_DIRECTORY=uk/co/uwcs/choob/support uk/co/uwcs/choob/support/ObjectDBClauseParser.jj; then
		echo "Failed to JJ-compile the ObjectDB Clause Parser."
		exit 1
	fi
fi

javac -J-Xmx64m  -classpath 'lib/*' uk/co/uwcs/choob/support/events/*.java uk/co/uwcs/choob/*.java uk/co/uwcs/choob/support/*.java uk/co/uwcs/choob/modules/*.java uk/co/uwcs/choob/plugins/*.java 
