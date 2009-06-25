@ECHO OFF

REM #####
REM # PLEASE UPDATE run.sh when you change this file (at least add a "# FIXME")
REM #####

:top
java -cp ^
.;^
lib/junit-4.5.jar;^
lib/c3p0-0.9.1.2.jar;^
lib/msnm.jar;^
lib/jcfd.jar;^
lib/jazzy-core.jar;^
lib/bsh-2.0b4.jar;^
lib/mysql-connector-java-5.1.5-bin.jar;^
lib/pircbot.jar;^
lib/js-rhino-1.6r2.jar;^
lib/jersey.jar;^
lib/jsr311-api.jar;^
lib/asm-3.2.jar;^
lib/asm-analysis-3.2.jar;^
lib/asm-commons-3.2.jar;^
lib/asm-tree-3.2.jar;^
lib/asm-util-3.2.jar;^
 ^
uk.co.uwcs.choob.ChoobMain
REM 0 = clean, normal exit. Die.
REM 1 = restart.
REM 2 = connection timed out.
IF "%1"=="once" GOTO :EOF
IF "%ERRORLEVEL%"=="0" GOTO :EOF
IF "%ERRORLEVEL%"=="2" EXIT /B 2
TIMEOUT /T 15 >nul
GOTO :top
