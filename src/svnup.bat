@ECHO OFF

SETLOCAL ENABLEEXTENSIONS ENABLEDELAYEDEXPANSION

REM This script updates the svn source and displays log changes
REM This is only useful for users of CLI based SVN clients
REM Stolen from Bjarni

REM === Read what version we have now to OldRev: ===

	svn info | FIND "Revision" > inforev
	FOR /F "tokens=2 delims=: " %%i IN (inforev) DO @SET OldRev=%%i
	DEL inforev
	ECHO Current Revision: %OldRev%.


REM === Update ===
	svn update > svn.log
	TYPE svn.log

REM === Get new revision to NewRev: ===
	svn info | FIND "Revision" > inforev
	FOR /F "tokens=2 delims=: " %%i IN (inforev) DO @SET NewRev=%%i
	DEL inforev
	ECHO New Revision: %NewRev%.

SET /A BaseIncd=OldRev + 1

IF NOT "%OldRev%" == "%NewRev%" svn log -v -r HEAD:%BaseIncd%

TYPE svn.log | FIND "G    "
TYPE svn.log | FIND "C    "

REM The windows pager sucks, let's not use it.