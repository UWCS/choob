@ECHO OFF

SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION
IF "%OS%"=="Windows_NT" CALL :setup-nt

SET Loop=0
IF "%1"=="loop" SET Loop=1

:top
ECHO ==== Compiling... ====
CALL compile
IF ERRORLEVEL 1 GOTO :compileerror
ECHO ==== Running... ====
CALL run
:compileerror

IF "%Loop%"=="0" GOTO :EOF
ECHO ==== Sleeping... ====
SLEEP 15
GOTO :top

:setup-nt
ECHO ==== Setting up environment... ====
SET ProgFiles=
SET JavaHome=

REM Get Program Files (32bit)...
SET ProgFiles=%ProgramFiles%
IF DEFINED ProgramFiles(x86) SET ProgFiles=%ProgramFiles(x86)%
ECHO Program files: %ProgFiles%

REM Get Java install...
IF EXIST "%ProgFiles%\Java" SET JavaHome=%ProgFiles%\Java
IF "x%JavaHome%"=="x" (
	ECHO ERROR finding Java home.
	EXIT /B 1
)

REM Find JDK v1.5 or later.
FOR /D %%P IN ("%JavaHome%\*") DO (
	SET FullPath=%%P
	SET Folder=%%~nP
	SET Ok=0
	IF "!Folder:~0,3!"=="jdk" (
		IF "!Folder:~3,1!" GTR "1" SET Ok=1
		IF "!Folder:~3,1!" EQU "1" IF "!Folder:~4,1!"=="." IF "!Folder:~5,1!" GEQ "5" SET Ok=1
		IF "!Ok!"=="1" (
			SET JavaHome=!FullPath!
			GOTO :found-java
		)
	)
)
:found-java
IF NOT EXIST "%JavaHome%\bin\javac.exe" (
	ECHO ERROR finding Java JDK 1.5 home.
	EXIT /B 1
)
SET JavaHome=%JavaHome%\bin

ECHO Java JDK bin : %JavaHome%
SET PATH=%PATH%;%JavaHome%
GOTO :EOF
