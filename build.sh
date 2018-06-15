#!/bin/bash
cd ./src/com/eviware/soapui && \
# Compiling Groovy Files
groovyc *.groovy && \
mkdir ./src/ && \
mv ./com/ ./src/ && \
# Moving Java and their class files to be packaged
cp -r ./report/ ./src/com/eviware/soapui/report && \
cp -r ./reports/ ./src/com/eviware/soapui/reports && \
cp -r ./tools/ ./src/com/eviware/soapui/tools && \
cd ./src/ && \
# Building JAR for SoapUI. To be placed in /Applications/SoapUI-5.4.0.app/Contents/java/app/lib/
jar -cvf custom-report.jar . && \
# Clean up
mv custom-report.jar ../../../../../ && \
cd .. && \
rm -rf ./src/
