package com.nottrilix.agent.telephony;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The VAD cuts the caller's audio into utterances and flags the onset of speech (barge-in); PCM helpers behave. */
class CallSessionTest {

    private static final TelephonyProperties.Vad VAD = new TelephonyProperties.Vad(500, 240, 700, 10_000);
    private static final int FRAME = 640; // 20 ms @ 16 kHz, 16-bit mono

    private static byte[] frame(int amplitude) {
        byte[] f = new byte[FRAME];
        for (int i = 0; i < FRAME / 2; i++) {
            int sample = (i % 2 == 0) ? amplitude : -amplitude; // square wave: RMS == amplitude
            f[2 * i] = (byte) (sample & 0xFF);
            f[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return f;
    }

    @Test
    void rmsOfSilenceAndOfASquareWave() {
        assertThat(PcmUtil.rms(frame(0))).isZero();
        assertThat(PcmUtil.rms(frame(2000))).isCloseTo(2000, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void speechAfterSilenceBecomesOneUtteranceWithPreRoll() {
        CallSession session = new CallSession("c1", 16_000, VAD);
        for (int i = 0; i < 20; i++) {
            assertThat(session.feed(frame(0)).utterance()).isNull();
        }
        boolean started = false;
        for (int i = 0; i < 25; i++) {               // 500 ms of speech
            CallSession.FeedResult r = session.feed(frame(2000));
            started |= r.speechStarted();
            assertThat(r.utterance()).isNull();
        }
        assertThat(started).as("onset flagged once speech lasted minSpeechMs").isTrue();
        byte[] utterance = null;
        for (int i = 0; i < 40 && utterance == null; i++) {   // 700 ms of silence ends it
            utterance = session.feed(frame(0)).utterance();
        }
        assertThat(utterance).isNotNull();
        assertThat(utterance.length % FRAME).isZero();
        // pre-roll (up to 15 frames) + remaining speech frames + 35 silence frames
        assertThat(utterance.length / FRAME).isBetween(25 + 35 - 15, 25 + 35 + 15);
    }

    @Test
    void shortNoiseIsNotSpeech() {
        CallSession session = new CallSession("c1", 16_000, VAD);
        for (int i = 0; i < 5; i++) {                 // 100 ms burst < minSpeechMs
            assertThat(session.feed(frame(2000)).speechStarted()).isFalse();
        }
        for (int i = 0; i < 50; i++) {
            assertThat(session.feed(frame(0)).utterance()).isNull();
        }
    }

    @Test
    void wavHeaderAndFraming() {
        byte[] pcm = new byte[1000];
        byte[] wav = PcmUtil.toWav(pcm, 16_000);
        assertThat(wav).hasSize(44 + 1000);
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        List<byte[]> frames = PcmUtil.chunkFrames(pcm, FRAME);
        assertThat(frames).hasSize(2);
        assertThat(frames.get(1)).hasSize(FRAME); // zero-padded tail
        CallSession session = new CallSession("c1", 16_000, VAD);
        session.enqueueAudio(pcm);
        assertThat(session.queuedMs()).isEqualTo(40);
        session.clearOutbound();
        assertThat(session.speaking()).isFalse();
    }

    @Test
    void streamedChunksBecomeWholeFramesWithoutGaps() {
        CallSession session = new CallSession("c1", 16_000, VAD);
        session.appendAudio(new byte[500]);            // less than a frame: nothing queued yet
        assertThat(session.queuedMs()).isZero();
        session.appendAudio(new byte[1000]);           // 1500 B = 2 frames + 220 B carried over
        assertThat(session.queuedMs()).isEqualTo(40);
        session.appendAudio(new byte[420]);            // 220 + 420 = exactly one frame
        assertThat(session.queuedMs()).isEqualTo(60);
        session.appendAudio(new byte[100]);
        session.flushAudio();                          // the 100 B tail is padded into a frame
        assertThat(session.queuedMs()).isEqualTo(80);
        assertThat(session.outbound).allSatisfy(frame -> assertThat(frame).hasSize(FRAME));
    }
}
