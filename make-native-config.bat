@echo off
cd target
java -agentlib:native-image-agent=config-output-dir=../src/main/resources/META-INF/native-image -jar PeerBanHelper.jar