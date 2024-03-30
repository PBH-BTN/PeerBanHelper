@echo off
cd target
java -agentlib:native-image-agent=config-merge-dir=../src/main/resources/META-INF/native-image -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Duser.language=en -Duser.region=US -jar PeerBanHelper.jar
pause