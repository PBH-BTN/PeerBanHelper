#!/bin/bash
apt-get update > /dev/null 2>&1
apt-get install -y python3-flask 2>&1

python3 test/fakeqb.py &

mv build/libs/PeerBanHelper.jar build/PeerBanHelper.jar
mv test/test-db build/data
sed -i 's#@@@@DATABESE_TYPE@@@@#${DATEBASE_TYPE}#g' build/data/config/config.yml

cd build
timeout 30 java -jar PeerBanHelper.jar nogui | tee -a pbh.log
if grep --quiet --fixed-strings "PeerBanHelper started in" pbh.log; then exit 1; fi;

exit 0