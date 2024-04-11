@echo off
cd target
java -agentlib:native-image-agent=config-merge-dir=../src/main/resources/META-INF/native-image -jar PeerBanHelper.jar
pause