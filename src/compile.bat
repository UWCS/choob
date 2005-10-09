@ECHO OFF

REM Windows version of icky compile script but it'll work. For now.

javac -classpath lib/jazzy-core.jar;lib/bsh-2.0b4.jar;lib/mysql-connector-java-3.1.10-bin.jar;lib/pircbot.jar;lib/js-rhino-1.6r2.jar org/uwcs/choob/*.java org/uwcs/choob/support/*.java org/uwcs/choob/modules/*.java org/uwcs/choob/plugins/*.java org/uwcs/choob/support/events/*.java %1 %2 %3
