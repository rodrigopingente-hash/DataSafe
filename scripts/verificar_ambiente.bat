@echo off
cd /d "%~dp0.."

echo === VERIFICACAO DO AMBIENTE ===
java -version
if errorlevel 1 goto erro

call mvn -version
if errorlevel 1 goto erro

echo.
echo Ambiente verificado.
pause
exit /b 0

:erro
echo ERRO: Java ou Maven indisponivel.
pause
exit /b 1