package io.ipfs.api.demo;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
This sample program demonstrates how to use the remote pinning API methods

rpc api - https://docs.ipfs.tech/reference/kubo/rpc/#api-v0-pin-remote-add

setup:
For demonstration purposes it uses a mock pinning service:
- https://github.com/ipfs-shipyard/js-mock-ipfs-pinning-service
Follow the instructions in the README.md file of the above repository for installation

Sample command to execute before running this program:
npx mock-ipfs-pinning-service --port 3000 --token secret

Note: The above parameters are referenced in the program below
 */
public class UsageRemotePinningAPI {

    public UsageRemotePinningAPI(IPFS ipfsClient) {
        try {
            run(ipfsClient);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    private void run(IPFS ipfs) throws IOException {

        // Add  file to the local node
        MerkleNode file = ipfs.add(new NamedStreamable.ByteArrayWrapper("file.txt", "test data".getBytes())).get(0);
        // Retrieve CID
        Multihash hash = file.hash;

        //Add the service
        String serviceName = "mock";
        ipfs.pin.remote.rmService(serviceName); //clean up if necessary
        ipfs.pin.remote.addService(serviceName, "http://127.0.0.1:3000", "secret");

        //List services
        List<Map> services = ipfs.pin.remote.lsService(true);
        for(Map service : services) {
            System.out.println(service);
        }

        // Pin
        Map addHashResult = ipfs.pin.remote.add(serviceName, hash, Optional.empty(), true);
        System.out.println(addHashResult);

        // List
        List<IPFS.PinStatus> statusList = List.of(IPFS.PinStatus.values()); // all statuses
        Map ls = ipfs.pin.remote.ls(serviceName, Optional.empty(), Optional.of(statusList));
        System.out.println(ls);

        // Remove pin from remote pinning service
        List<IPFS.PinStatus> queued = List.of(IPFS.PinStatus.queued);
        ipfs.pin.remote.rm(serviceName, Optional.empty(), Optional.of(queued), Optional.of(List.of(hash)));

    }

    public static void main(String[] args) {
        IPFS ipfsClient = new IPFS(new MultiAddress("/ip4/127.0.0.1/tcp/5001"));
        new UsageRemotePinningAPI(ipfsClient);
    }
}
