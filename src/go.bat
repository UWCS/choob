@ECHO OFF

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
