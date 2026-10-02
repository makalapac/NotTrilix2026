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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

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
    private volatile byte[] filler;
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
        AudioTranscriptionResponse response = stt.call(new AudioTranscriptionPrompt(resource));
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

    /** "Samo trenutak." while the agent is still thinking; synthesized once. */
    byte[] filler() {
        byte[] cached = filler;
        if (cached == null) {
            cached = speak(props.fillerText());
            filler = cached;
        }
        return cached;
    }
}
