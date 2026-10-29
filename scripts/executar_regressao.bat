@echo off
cd /d "%~dp0.."

call mvn exec:java "-Dexec.mainClass=pt.unl.fct.di.syspriv.RegressionExperiment"

pause