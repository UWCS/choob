@ECHO OFF

REM #####
REM # PLEASE UPDATE compile.sh when you change this file (at least add a "# FIXME")
REM #####

REM Windows version of icky compile script but it'll work. For now.

REM This checks which file is newer. It's not nice, but it'll work.
CALL :compare-file-dates misc\HorriblePerlScript.java uk\co\uwcs\choob\Choob.java
IF "%CompareFileDates%"=="1" (
	ECHO Rebuild event system...
	javac -d misc misc/HorriblePerlScript.java
	java -cp misc uk.co.uwcs.choob.misc.HorriblePerlScript
)

CALL :compare-file-dates uk\co\uwcs\choob\support\ObjectDbClauseParser.jjt uk\co\uwcs\choob\support\ObjectDbClauseParser.java
IF "%CompareFileDates%"=="1" (
	ECHO Recompiling ObjectDB parser...
	jjtree -JDK_VERSION:"1.5" -OUTPUT_DIRECTORY:uk/co/uwcs/choob/support uk/co/uwcs/choob/support/ObjectDbClauseParser.jjt
	IF NOT "%ERRORLEVEL%"=="0" (
		ECHO Failed to JJT-compile the ObjectDB Clause Parser.
		EXIT /B 1
	)
	javacc -JDK_VERSION:"1.5" -OUTPUT_DIRECTORY:uk/co/uwcs/choob/support uk/co/uwcs/choob/support/ObjectDbClauseParser.jj
	IF NOT "%ERRORLEVEL%"=="0" (
		ECHO Failed to JJ-compile the ObjectDB Clause Parser.
		EXIT /B 1
	)
)

javac -classpath .;lib/c3p0-0.9.0.2.jar;lib/msnm.jar;lib/jcfd.jar;lib/jazzy-core.jar;lib/bsh-2.0b4.jar;lib/mysql-connector-java-3.1.12-bin.jar;lib/pircbot.jar;lib/js-rhino-1.6r2.jar uk/co/uwcs/choob/*.java uk/co/uwcs/choob/support/*.java uk/co/uwcs/choob/modules/*.java uk/co/uwcs/choob/plugins/*.java uk/co/uwcs/choob/support/events/*.java %1 %2 %3
GOTO :EOF


REM ##### Utility functions:

:compare-file-dates
REM Results (in %CompareFiles%):
REM   1 First file is newer.
REM   2 Second file is newer.

REM Copy files to the same location, so we can use dir /o:d to sort by date.
COPY %1 cmpf.1 >nul
COPY %2 cmpf.2 >nul

REM Compare both ways, since if they have the *same* time, the resulting dir
REM output will be the order we listed the filenames.
SET CompareFileDates1=0
SET CompareFileDates2=0
FOR /F "usebackq delims=. tokens=2" %%f IN (`DIR cmpf.1 cmpf.2 /o:d /b`) DO (
	SET CompareFileDates1=%%f
)
FOR /F "usebackq delims=. tokens=2" %%f IN (`DIR cmpf.2 cmpf.1 /o:d /b`) DO (
	SET CompareFileDates2=%%f
)
REM If the order affected the answer, they are the same date.
SET CompareFileDates=%CompareFileDates1%
IF NOT "%CompareFileDates1%"=="%CompareFileDates2%" SET CompareFileDates=0

REM Clean up, and return.
DEL cmpf.1 cmpf.2
SET CompareFileDates1=
SET CompareFileDates2=
GOTO :EOF