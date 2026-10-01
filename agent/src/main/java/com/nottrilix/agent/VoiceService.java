package com.nottrilix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Voice channel in front of the same text agent: recording -> speech-to-text (STT) -> AgentService (text in,
 * text out) -> text-to-speech (TTS) -> mp3. Both go through Spring AI (TranscriptionModel, TextToSpeechModel).
 * The customer's recording lives only in this call's memory: never stored, never logged. Only the transcript is logged.
 * <p>
 * Two shapes of the same turn: {@link #turn} returns everything at once (JSON with base64 mp3), {@link #turnStreaming}
 * writes JSON event lines (transcript as soon as STT is done, the reply as soon as the agent is done) and then the
 * speech bytes as the TTS produces them, so the browser can start playing after the first chunk.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger("VOICE");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MIME_TYPE = "audio/mpeg";

    /** Speech as mp3 + who synthesized it. */
    public record Speech(byte[] audio, String mimeType, String provider) {
    }

    /** The voice channel is not configured (no STT/TTS model, or it is disabled). */
    public static class VoiceUnavailableException extends RuntimeException {
        public VoiceUnavailableException(String message) {
            super(message);
        }
    }

    private final AgentService agent;
    private final TranscriptionModel stt;
    private final TextToSpeechModel tts;
    private final VoiceProperties props;
    private volatile byte[] filler;

    public VoiceService(AgentService agent, ObjectProvider<TranscriptionModel> stt,
                        ObjectProvider<TextToSpeechModel> tts, VoiceProperties props) {
        this.agent = agent;
        this.stt = stt.getIfAvailable();
        this.tts = tts.getIfAvailable();
        this.props = props;
        log.info("VOICE {}: STT={}, TTS={}", props.enabled() ? "ukljucen" : "iskljucen",
                this.stt == null ? "-" : this.stt.getClass().getSimpleName(),
                this.tts == null ? "-" : this.tts.getClass().getSimpleName());
    }

    public boolean isAvailable() {
        return props.enabled() && stt != null && tts != null;
    }

    public AgentController.VoiceTurnResponse turn(String customerToken, String conversationId, byte[] audio,
                                                  String filename, String channel) {
        if (!isAvailable()) {
            throw new VoiceUnavailableException("Glasovni kanal nije dostupan: STT/TTS model nije konfiguriran");
        }
        long start = System.nanoTime();
        String transcript = transcribe(audio, filename);
        long sttMs = (System.nanoTime() - start) / 1_000_000;
        log.info("VOICE STT {} B ({}) -> {} ms: '{}'", audio.length, filename, sttMs, transcript);
        if (transcript.isBlank()) {
            throw new AgentUnavailableException("Nisam razumjela snimku. Pokušajte ponovno, bliže mikrofonu.");
        }

        AgentChatResponse chat = agent.chat(customerToken, conversationId, transcript, channel);

        long ttsStart = System.nanoTime();
        Speech speech = speak(chat.reply());
        long ttsMs = (System.nanoTime() - ttsStart) / 1_000_000;
        log.info("VOICE TTS {} -> {} B ({}) u {} ms", speech.provider(), speech.audio().length, speech.mimeType(), ttsMs);
        return new AgentController.VoiceTurnResponse(transcript, chat,
                Base64.getEncoder().encodeToString(speech.audio()), speech.mimeType(), sttMs, ttsMs,
                speech.provider());
    }

    /**
     * The same turn as a stream: {@code {"type":"transcript",...}}, {@code {"type":"chat",...}},
     * {@code {"type":"audio",...}} each on its own line, then raw mp3 until the end. A failure after the response
     * has started becomes an {@code {"type":"error",...}} line instead of an HTTP status.
     */
    public void turnStreaming(String customerToken, String conversationId, byte[] audio, String filename,
                              String channel, OutputStream out) throws IOException {
        if (!isAvailable()) {
            writeEvent(out, event("error", "error", "VOICE_UNAVAILABLE",
                    "message", "Glasovni kanal nije dostupan: STT/TTS model nije konfiguriran"));
            return;
        }
        long start = System.nanoTime();
        String transcript;
        try {
            transcript = transcribe(audio, filename);
        } catch (RuntimeException e) {
            log.error("VOICE STT neuspio: {}", e.toString());
            writeEvent(out, event("error", "error", "VOICE_UNAVAILABLE",
                    "message", "Prepoznavanje govora trenutno ne radi. Pokušajte ponovno ili napišite poruku."));
            return;
        }
        long sttMs = (System.nanoTime() - start) / 1_000_000;
        log.info("VOICE STT {} B ({}) -> {} ms: '{}'", audio.length, filename, sttMs, transcript);
        if (transcript.isBlank()) {
            writeEvent(out, event("error", "error", "EMPTY_TRANSCRIPT",
                    "message", "Nisam razumjela snimku. Pokušajte ponovno, bliže mikrofonu."));
            return;
        }
        writeEvent(out, event("transcript", "transcript", transcript, "sttMs", sttMs));

        AgentChatResponse chat;
        try {
            chat = agent.chat(customerToken, conversationId, transcript, channel);
        } catch (RuntimeException e) {
            log.error("VOICE agent neuspio: {}", e.toString());
            writeEvent(out, event("error", "error", errorCode(e), "message", e.getMessage()));
            return;
        }
        String spoken = SpeechText.forSpeech(chat.reply(), props.maxReplyChars());
        writeEvent(out, event("chat", "chat", chat, "ttsProvider", provider(), "spoken", spoken));
        writeEvent(out, event("audio", "mimeType", MIME_TYPE));

        long ttsStart = System.nanoTime();
        long firstChunkMs = -1;
        int bytes = 0;
        int chunks = 0;
        try {
            for (TextToSpeechResponse response : tts.stream(new TextToSpeechPrompt(spoken)).toIterable()) {
                byte[] chunk = response.getResult() == null ? null : response.getResult().getOutput();
                if (chunk == null || chunk.length == 0) {
                    continue;
                }
                if (firstChunkMs < 0) {
                    firstChunkMs = (System.nanoTime() - ttsStart) / 1_000_000;
                }
                out.write(chunk);
                out.flush();
                bytes += chunk.length;
                chunks++;
            }
        } catch (RuntimeException e) {
            // the text already reached the browser; the speech simply ends early
            log.error("VOICE TTS stream {} neuspio nakon {} B: {}", provider(), bytes, e.toString());
        }
        log.info("VOICE TTS {} stream -> {} B u {} dijelova, prvi zvuk nakon {} ms, ukupno {} ms", provider(), bytes,
                chunks, firstChunkMs, (System.nanoTime() - ttsStart) / 1_000_000);
    }

    /** Nola's short bridging phrase ("Samo trenutak.") as mp3, synthesized once per process. */
    public byte[] filler() {
        byte[] cached = filler;
        if (cached == null) {
            if (!isAvailable() || props.fillerText() == null || props.fillerText().isBlank()) {
                throw new VoiceUnavailableException("Glasovni kanal nije dostupan");
            }
            cached = speak(props.fillerText()).audio();
            filler = cached;
            log.info("VOICE filler '{}' -> {} B", props.fillerText(), cached.length);
        }
        return cached;
    }

    /** Speech-to-text; the file name (extension) tells the model the recording format. */
    String transcribe(byte[] audio, String filename) {
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        AudioTranscriptionResponse response = stt.call(new AudioTranscriptionPrompt(resource));
        String text = response.getResult() == null ? "" : response.getResult().getOutput();
        return text == null ? "" : text.strip();
    }

    /** Text-to-speech in one piece; amounts and symbols are spoken as words, a long reply is cut (see SpeechText). */
    Speech speak(String text) {
        String spoken = SpeechText.forSpeech(text, props.maxReplyChars());
        TextToSpeechResponse response = tts.call(new TextToSpeechPrompt(spoken));
        byte[] audio = response.getResult() == null ? new byte[0] : response.getResult().getOutput();
        return new Speech(audio, MIME_TYPE, provider());
    }

    public String provider() {
        return tts == null ? "-" : tts.getClass().getSimpleName().replace("TextToSpeechModel", "")
                .replace("AudioSpeechModel", "");
    }

    private static String errorCode(RuntimeException e) {
        if (e instanceof SystemGateway.InvalidTokenException) {
            return "UNAUTHORIZED";
        }
        if (e instanceof SystemGateway.SystemUnavailableException) {
            return "SYSTEM_UNAVAILABLE";
        }
        if (e instanceof AgentUnavailableException) {
            return "AGENT_UNAVAILABLE";
        }
        return "AGENT_ERROR";
    }

    private static Map<String, Object> event(String type, Object... keyValues) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            event.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return event;
    }

    private static void writeEvent(OutputStream out, Map<String, Object> event) throws IOException {
        out.write(JSON.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }
}
