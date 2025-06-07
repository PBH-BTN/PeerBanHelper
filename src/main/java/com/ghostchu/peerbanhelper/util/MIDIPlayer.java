package com.ghostchu.peerbanhelper.util;

import lombok.extern.slf4j.Slf4j;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public final class MIDIPlayer implements AutoCloseable {
    private final InputStream[] sequence;
    private Sequencer midip = null;
    private int currentIndex = -1;

    public MIDIPlayer(InputStream... sequences) {
        this.sequence = sequences;
        try {
            this.midip = MidiSystem.getSequencer();
        } catch (MidiUnavailableException e) {
            log.error("This platform doesn't support MIDI playback", e);
        }
    }

    public void play() throws MidiUnavailableException {
        if (midip == null) {
            log.error("MIDI playback is not supported on this platform, skipping playback");
            return;
        }
        midip.open();
        // when finished, play ABOUT-MiSide-MusicMenu-Update.mid
        midip.addMetaEventListener(meta -> {
            if (meta.getType() == 47) {
                try {
                    nextSequence();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        nextSequence();
    }

    public void stop() {
        if (midip != null && midip.isRunning())
            midip.stop();
    }

    private void nextSequence() {
        if (midip.isRunning())
            midip.stop();
        currentIndex++;
        if (currentIndex < sequence.length) {
            try {
                var sequencer = MidiSystem.getSequence(sequence[currentIndex]);
                midip.setSequence(sequencer);
                if (!midip.isRunning())
                    midip.start();
            } catch (Exception e) {
                log.error("Failed to play MIDI sequence", e);
            }
        }
    }

    @Override
    public void close() {
        stop();
        for (InputStream inputStream : sequence) {
            try (inputStream) {
                // do nothing, just for auto close
            } catch (IOException ignored) {
            }
        }
    }
}
