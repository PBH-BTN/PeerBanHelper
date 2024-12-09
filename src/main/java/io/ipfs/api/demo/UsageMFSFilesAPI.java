package io.ipfs.api.demo;

import io.ipfs.api.IPFS;
import io.ipfs.api.NamedStreamable;
import io.ipfs.api.WriteFilesArgs;
import io.ipfs.multiaddr.MultiAddress;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
From MFS api documentation: https://github.com/ipfs/js-ipfs/blob/master/docs/core-api/FILES.md#the-mutable-files-api
The Mutable File System (MFS) is a virtual file system on top of IPFS that exposes a Unix like API over a virtual directory.
It enables users to write and read from paths without having to worry about updating the graph.

Useful links:
rpc api - https://docs.ipfs.tech/reference/kubo/rpc/#getting-started
proto.school - https://proto.school/mutable-file-system/01
ipfs.tech - https://docs.ipfs.tech/concepts/file-systems/#mutable-file-system-mfs

 */
public class UsageMFSFilesAPI {

    public UsageMFSFilesAPI(IPFS ipfsClient) {
        try {
            run(ipfsClient);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
    private void run(IPFS ipfs) throws IOException {

        // remove 'my' directory to clean up from a previous run
        ipfs.files.rm("/my", true, true);

        // To create a new directory nested under others that don't yet exist, you need to explicitly set the value of parents to true
        ipfs.files.mkdir("/my/directory/example", true);

        // Check directory status
        String directoryPath = "/my/directory/example";
        Map exampleDirectory = ipfs.files.stat(directoryPath);
        //{Hash=QmV1a2QoUnB9fPzjZd1GunGR53isuhcWWNCS5Bg3mJyv8N, Size=0, CumulativeSize=57, Blocks=1, Type=directory}

        // Add a file
        String contents = "hello world!";
        String filename = "hello.txt";
        String filePath = directoryPath + "/" + filename;
        NamedStreamable ns = new NamedStreamable.ByteArrayWrapper(filename, contents.getBytes());
        ipfs.files.write(filePath, ns, true, true);

        // Read contents of a file
        String fileContents = new String(ipfs.files.read(filePath));
        System.out.println(fileContents);

        // Write a file using builder pattern
        String ipfsFilename = "ipfs.txt";
        String fullIpfsPath = directoryPath + "/" + ipfsFilename;
        NamedStreamable ipfsFile = new NamedStreamable.ByteArrayWrapper(ipfsFilename, "ipfs says hello".getBytes());
        WriteFilesArgs args = WriteFilesArgs.Builder.newInstance()
                .setCreate()
                .setParents()
                .build();
        ipfs.files.write(fullIpfsPath, ipfsFile, args);

        // List directory contents
        List<Map> ls = ipfs.files.ls(directoryPath);
        for(Map entry : ls) {
            System.out.println(entry.get("Name"));
        }

        // Copy file to another directory
        String copyDirectoryPath = "/my/copy/";
        ipfs.files.cp(filePath, copyDirectoryPath + filename, true);
        ls = ipfs.files.ls(copyDirectoryPath);
        for(Map entry : ls) {
            System.out.println(entry.get("Name"));
        }

        // Move file to another directory
        String duplicateDirectoryPath = "/my/duplicate/";
        ipfs.files.mkdir(duplicateDirectoryPath, false);
        ipfs.files.mv(copyDirectoryPath + filename, duplicateDirectoryPath + filename);
        ls = ipfs.files.ls(duplicateDirectoryPath);
        for(Map entry : ls) {
            System.out.println(entry.get("Name"));
        }

        // Remove a directory
        ipfs.files.rm(copyDirectoryPath, true, true);
        ls = ipfs.files.ls("/my");
        for(Map entry : ls) {
            System.out.println(entry.get("Name"));
        }
    }
    public static void main(String[] args) {
        IPFS ipfsClient = new IPFS(new MultiAddress("/ip4/127.0.0.1/tcp/5001"));
        new UsageMFSFilesAPI(ipfsClient);
    }
}
