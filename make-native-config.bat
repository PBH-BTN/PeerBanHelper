@echo off
cd target
java -agentlib:native-image-agent=config-merge-dir=../src/main/resources/META-INF/native-image -Duser.language=en -Duser.region=US -jar PeerBanHelper.jar