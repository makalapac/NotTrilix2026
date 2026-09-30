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

import java.util.Base64;

/**
 * Glasovni kanal ispred istog tekstualnog agenta: snimka -> prepoznavanje govora (STT) -> AgentService (tekst unutra,
 * tekst van) -> sinteza govora (TTS) -> mp3. Oboje kroz Spring AI (TranscriptionModel, TextToSpeechModel).
 * Snimka kupca zivi samo u memoriji ovog poziva: ne pohranjuje se i ne logira. U trag ide transkript.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger("VOICE");

    /** Govor kao mp3 + tko ga je sintetizirao. */
    public record Speech(byte[] audio, String mimeType, String provider) {
    }

    /** Glasovni kanal nije konfiguriran (nema STT/TTS modela ili je iskljucen). */
    public static class VoiceUnavailableException extends RuntimeException {
        public VoiceUnavailableException(String message) {
            super(message);
        }
    }

    private final AgentService agent;
    private final TranscriptionModel stt;
    private final TextToSpeechModel tts;
    private final VoiceProperties props;

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

    /** Prepoznavanje govora; naziv datoteke (ekstenzija) govori modelu o formatu snimke. */
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

    /** Sinteza govora; dugacak odgovor se za govor skrati (kupac cijeli tekst vidi u chatu). */
    Speech speak(String text) {
        String spoken = text.length() <= props.maxReplyChars() ? text
                : text.substring(0, props.maxReplyChars()).replaceAll("\\s+\\S*$", "") + "…";
        TextToSpeechResponse response = tts.call(new TextToSpeechPrompt(spoken));
        byte[] audio = response.getResult() == null ? new byte[0] : response.getResult().getOutput();
        return new Speech(audio, "audio/mpeg", provider());
    }

    public String provider() {
        return tts == null ? "-" : tts.getClass().getSimpleName().replace("TextToSpeechModel", "")
                .replace("AudioSpeechModel", "");
    }
}
