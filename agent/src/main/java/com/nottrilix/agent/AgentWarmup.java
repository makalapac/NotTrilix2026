package com.nottrilix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Warms the model and the transcription client once at startup. The first turn of the first call after a start took
 * 4-6 s (fresh connections, JIT), later turns 2-3 s; on stage the first call is the demo, so the cold start is paid
 * here, not in front of the audience. One tiny completion and one transcription of 0.3 s of silence.
 */
@Component
class AgentWarmup {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

    private final ChatModel chatModel;
    private final TranscriptionModel stt;

    AgentWarmup(ObjectProvider<ChatModel> chatModel, ObjectProvider<TranscriptionModel> stt) {
        this.chatModel = chatModel.getIfAvailable();
        this.stt = stt.getIfAvailable();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread.startVirtualThread(() -> {
            long modelMs = -1;
            long sttMs = -1;
            if (chatModel != null) {
                try {
                    long start = System.nanoTime();
                    chatModel.call("Odgovori jednom riječju: spremno?");
                    modelMs = (System.nanoTime() - start) / 1_000_000;
                } catch (RuntimeException e) {
                    log.warn("AGENT zagrijavanje modela nije uspjelo: {}", e.getMessage());
                }
            }
            if (stt != null) {
                try {
                    long start = System.nanoTime();
                    stt.call(new AudioTranscriptionPrompt(new ByteArrayResource(silentWav(300)) {
                        @Override
                        public String getFilename() {
                            return "warmup.wav";
                        }
                    }));
                    sttMs = (System.nanoTime() - start) / 1_000_000;
                } catch (RuntimeException e) {
                    log.warn("AGENT zagrijavanje STT-a nije uspjelo: {}", e.getMessage());
                }
            }
            log.info("AGENT zagrijan: model {} ms, STT {} ms", modelMs, sttMs);
        });
    }

    /** A 16 kHz mono 16-bit WAV of digital silence. */
    static byte[] silentWav(int millis) {
        int rate = 16000;
        int dataBytes = rate * 2 * millis / 1000;
        ByteBuffer wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes())
                .put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(dataBytes);
        return wav.array();
    }
}
