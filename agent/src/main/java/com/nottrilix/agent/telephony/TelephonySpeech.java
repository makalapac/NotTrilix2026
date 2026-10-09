package com.nottrilix.agent.telephony;

import com.nottrilix.agent.SpeechText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechModel;
import org.springframework.ai.elevenlabs.ElevenLabsTextToSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Speech for the phone line, through the same Spring AI models as the browser voice: the caller's utterance (PCM from
 * the media stream) goes to the TranscriptionModel as WAV; the reply comes back from ElevenLabs as raw PCM at the
 * line's sample rate (pcm_16000), so nothing is transcoded on the way to the caller.
 */
@Component
class TelephonySpeech {

    private static final Logger log = LoggerFactory.getLogger("TELEPHONY");
    private static final int MAX_SPOKEN_CHARS = 600;

    private final TranscriptionModel stt;
    private final TextToSpeechModel tts;
    private final TelephonyProperties props;
    private volatile List<byte[]> fillers;
    private final AtomicInteger nextFiller = new AtomicInteger();
    private volatile byte[] greetingUnknown;

    TelephonySpeech(ObjectProvider<TranscriptionModel> stt, ObjectProvider<TextToSpeechModel> tts,
                    TelephonyProperties props) {
        this.stt = stt.getIfAvailable();
        this.tts = tts.getIfAvailable();
        this.props = props;
        if (!available()) {
            log.warn("TELEPHONY govor nije dostupan: STT={} TTS={} (telefon traži ElevenLabs za PCM izlaz)",
                    this.stt == null ? "-" : this.stt.getClass().getSimpleName(),
                    this.tts == null ? "-" : this.tts.getClass().getSimpleName());
        }
    }

    /** Synthesizes the cached phrases once at startup, so the first call does not pay for them mid-call. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!available()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                long start = System.nanoTime();
                filler();
                greetingUnknown();
                log.info("TELEPHONY govor zagrijan: {} i pozdrav nepoznatom sintetizirani ({} ms)", props.fillerTexts(),
                        (System.nanoTime() - start) / 1_000_000);
            } catch (RuntimeException e) {
                log.warn("TELEPHONY zagrijavanje govora nije uspjelo: {}", e.getMessage());
            }
        });
    }

    boolean available() {
        return stt != null && tts instanceof ElevenLabsTextToSpeechModel;
    }

    String transcribe(byte[] pcm, int sampleRate) {
        byte[] wav = PcmUtil.toWav(pcm, sampleRate);
        ByteArrayResource resource = new ByteArrayResource(wav) {
            @Override
            public String getFilename() {
                return "utterance.wav";
            }
        };
        // a phone-specific model when configured; language and the domain prompt stay from the defaults
        AudioTranscriptionPrompt prompt = props.sttModel() == null || props.sttModel().isBlank()
                ? new AudioTranscriptionPrompt(resource)
                : new AudioTranscriptionPrompt(resource, OpenAiAudioTranscriptionOptions.builder().model(props.sttModel()).build());
        AudioTranscriptionResponse response = stt.call(prompt);
        String text = response.getResult() == null ? "" : response.getResult().getOutput();
        return text == null ? "" : text.strip();
    }

    /** Raw 16-bit PCM at the line's sample rate; amounts and symbols are spoken as words (SpeechText). */
    byte[] speak(String text) {
        TextToSpeechResponse response = tts.call(prompt(text));
        byte[] pcm = response.getResult() == null ? null : response.getResult().getOutput();
        return pcm == null ? new byte[0] : pcm;
    }

    /**
     * The same, streamed: PCM chunks go to the sink as the synthesis produces them, so the first frames reach the
     * caller a few hundred milliseconds after the text is known. Returns the number of bytes synthesized.
     */
    int speakStreaming(String text, Consumer<byte[]> sink) {
        int bytes = 0;
        for (TextToSpeechResponse response : tts.stream(prompt(text)).toIterable()) {
            byte[] chunk = response.getResult() == null ? null : response.getResult().getOutput();
            if (chunk != null && chunk.length > 0) {
                sink.accept(chunk);
                bytes += chunk.length;
            }
        }
        return bytes;
    }

    private TextToSpeechPrompt prompt(String text) {
        String spoken = SpeechText.forSpeech(text, MAX_SPOKEN_CHARS);
        ElevenLabsTextToSpeechOptions options = ElevenLabsTextToSpeechOptions.builder()
                .outputFormat("pcm_" + props.sampleRate())
                .build();
        return new TextToSpeechPrompt(spoken, options);
    }

    byte[] greeting(String firstName) {
        return speak(greetingText(firstName));
    }

    void greetingStreaming(String firstName, Consumer<byte[]> sink) {
        speakStreaming(greetingText(firstName), sink);
    }

    private String greetingText(String firstName) {
        String name = firstName == null || firstName.isBlank() ? "" : " " + firstName;
        return props.greeting().replace("{name}", name);
    }

    byte[] greetingUnknown() {
        byte[] cached = greetingUnknown;
        if (cached == null) {
            cached = speak(props.greetingUnknown());
            greetingUnknown = cached;
        }
        return cached;
    }

    /** A short "still here" phrase while the agent is thinking: the configured phrases in rotation, synthesized once. */
    byte[] filler() {
        List<byte[]> cached = fillers;
        if (cached == null) {
            cached = props.fillerTexts().stream().map(this::speak).toList();
            fillers = cached;
        }
        if (cached.isEmpty()) {
            return new byte[0];
        }
        return cached.get(Math.floorMod(nextFiller.getAndIncrement(), cached.size()));
    }
}
