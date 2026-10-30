@echo off
rem Needs a JDK (javac), version 17 or newer. Run from the project root.
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt && java -cp bin b2blab.Main %*
del sources.txt
