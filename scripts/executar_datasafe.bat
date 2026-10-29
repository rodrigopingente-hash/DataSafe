@echo off
cd /d "%~dp0.."

call mvn compile exec:java "-Dexec.mainClass=pt.unl.fct.di.syspriv.DataSafe"

pause