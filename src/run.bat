@ECHO OFF

REM #####
REM # PLEASE UPDATE run.sh when you change this file (at least add a "# FIXME")
REM #####

:top
java -cp .;lib/c3p0-0.9.0.2.jar;lib/msnm.jar;lib/jcfd.jar;lib/jazzy-core.jar;lib/bsh-2.0b4.jar;lib/mysql-connector-java-3.1.10-bin.jar;lib/pircbot.jar;lib/js-rhino-1.6r2.jar org.uwcs.choob.ChoobMain
IF "%ERRORLEVEL%"=="0" GOTO :EOF
SLEEP 15
GOTO :top
