package com.ghostchu.peerbanhelper.util.scriptengine.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxProtocolTest {
    @ParameterizedTest
    @CsvSource(value = {
            ".av|return peer.uploaded > 3;",
            ".js|peer?.uploaded > 3",
            ".lua|return peer.uploaded > 3"
    }, delimiter = '|')
    void compiledWorkerHandlesConsecutivePeerSnapshots(String extension, String source) throws IOException {
        var input = new ByteArrayOutputStream();
        var output = new ByteArrayOutputStream();
        SandboxProtocol.write(input, new SandboxProtocol.Init(extension, "test" + extension, source, "btn"));
        SandboxProtocol.write(input, Map.of("peer", Map.of("uploaded", 10L)));
        SandboxProtocol.write(input, Map.of("peer", Map.of("uploaded", 2L)));
        SandboxWorker.serve(new ByteArrayInputStream(input.toByteArray()), output);
        var responses = new ByteArrayInputStream(output.toByteArray());
        assertEquals("test" + extension, SandboxProtocol.read(responses, SandboxProtocol.Metadata.class).name());
        assertEquals(new SandboxProtocol.Reply(true, null), SandboxProtocol.read(responses, SandboxProtocol.Reply.class));
        assertEquals(new SandboxProtocol.Reply(false, null), SandboxProtocol.read(responses, SandboxProtocol.Reply.class));
        assertEquals(0, responses.available());
    }

    @Test
    void rejectsOversizedTruncatedAndNonScalarReplies() throws IOException {
        assertThrows(IOException.class, () -> SandboxProtocol.read(new ByteArrayInputStream(new byte[]{127, -1, -1, -1}), SandboxProtocol.Reply.class));
        assertThrows(EOFException.class, () -> SandboxProtocol.read(new ByteArrayInputStream(new byte[]{0, 0, 0, 1}), Map.class));
        var output = new ByteArrayOutputStream();
        SandboxProtocol.write(output, new SandboxProtocol.Reply(Map.of("unexpected", 42), null));
        assertThrows(IOException.class, () -> SandboxProtocol.read(new ByteArrayInputStream(output.toByteArray()), SandboxProtocol.Reply.class));
    }
}
