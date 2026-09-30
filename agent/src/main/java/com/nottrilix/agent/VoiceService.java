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
 * Voice channel in front of the same text agent: recording -> speech-to-text (STT) -> AgentService (text in,
 * text out) -> text-to-speech (TTS) -> mp3. Both go through Spring AI (TranscriptionModel, TextToSpeechModel).
 * The customer's recording lives only in this call's memory: never stored, never logged. Only the transcript is logged.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger("VOICE");

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

    /** Text-to-speech; a long reply is shortened for speech (the customer sees the full text in chat). */
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
