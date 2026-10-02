package com.nottrilix.agent.telephony;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Media-plane state of one call: the outbound frame queue (drained at real-time pace by the handler) and a small
 * energy-based voice activity detector that cuts the caller's audio into utterances. The caller's audio is kept only
 * for the utterance being detected; nothing is stored.
 */
final class CallSession {

    /** What one inbound frame changed: speech just started (barge-in) and/or an utterance is complete. */
    record FeedResult(boolean speechStarted, byte[] utterance) {
    }

    private static final int FRAME_MS = 20;
    private static final int PRE_ROLL_FRAMES = 15; // ~300 ms of context before the detected onset

    final String callId;
    final int sampleRate;
    final int frameBytes;
    final Queue<byte[]> outbound = new ConcurrentLinkedQueue<>();

    private final double speechRms;
    private final int minSpeechMs;
    private final int endSilenceMs;
    private final int maxUtteranceMs;

    // VAD state, touched only from the WebSocket thread
    private final Deque<byte[]> preRoll = new ArrayDeque<>();
    private final ByteArrayOutputStream utterance = new ByteArrayOutputStream();
    // streamed outbound audio: the partial frame left over from the last chunk (one producer at a time)
    private final ByteArrayOutputStream carry = new ByteArrayOutputStream();
    private boolean inUtterance;
    private int speechMs;
    private int silenceMs;
    private int utteranceMs;

    CallSession(String callId, int sampleRate, TelephonyProperties.Vad vad) {
        this.callId = callId;
        this.sampleRate = sampleRate;
        this.frameBytes = sampleRate * 2 * FRAME_MS / 1000;
        this.speechRms = vad.speechRms();
        this.minSpeechMs = vad.minSpeechMs();
        this.endSilenceMs = vad.endSilenceMs();
        this.maxUtteranceMs = vad.maxUtteranceMs();
    }

    /** Feeds one inbound 20 ms frame. */
    FeedResult feed(byte[] frame) {
        boolean loud = PcmUtil.rms(frame) >= speechRms;
        if (inUtterance) {
            utterance.writeBytes(frame);
            utteranceMs += FRAME_MS;
            silenceMs = loud ? 0 : silenceMs + FRAME_MS;
            if (silenceMs >= endSilenceMs || utteranceMs >= maxUtteranceMs) {
                byte[] done = utterance.toByteArray();
                reset();
                return new FeedResult(false, done);
            }
            return new FeedResult(false, null);
        }
        // not in an utterance: keep a short pre-roll and wait for sustained speech
        preRoll.addLast(frame);
        if (preRoll.size() > PRE_ROLL_FRAMES) {
            preRoll.removeFirst();
        }
        if (!loud) {
            speechMs = 0;
            return new FeedResult(false, null);
        }
        speechMs += FRAME_MS;
        if (speechMs < minSpeechMs) {
            return new FeedResult(false, null);
        }
        inUtterance = true;
        silenceMs = 0;
        utteranceMs = 0;
        preRoll.forEach(utterance::writeBytes); // the onset, including the frames that triggered detection
        preRoll.clear();
        return new FeedResult(true, null);
    }

    private void reset() {
        utterance.reset();
        inUtterance = false;
        speechMs = 0;
        silenceMs = 0;
        utteranceMs = 0;
    }

    /** Queues agent audio for the pacer, cut into 20 ms frames (the tail is padded with silence). */
    void enqueueAudio(byte[] pcm) {
        outbound.addAll(PcmUtil.chunkFrames(pcm, frameBytes));
    }

    /**
     * Queues streamed agent audio: chunks of any size arrive as the synthesis produces them; only whole frames are
     * queued and the remainder waits for the next chunk, so there are no gaps between chunks. {@link #flushAudio}
     * pads and queues the last partial frame.
     */
    void appendAudio(byte[] chunk) {
        carry.writeBytes(chunk);
        if (carry.size() < frameBytes) {
            return;
        }
        byte[] buffered = carry.toByteArray();
        int whole = buffered.length - buffered.length % frameBytes;
        for (int off = 0; off < whole; off += frameBytes) {
            byte[] frame = new byte[frameBytes];
            System.arraycopy(buffered, off, frame, 0, frameBytes);
            outbound.add(frame);
        }
        carry.reset();
        carry.write(buffered, whole, buffered.length - whole);
    }

    void flushAudio() {
        if (carry.size() > 0) {
            outbound.addAll(PcmUtil.chunkFrames(carry.toByteArray(), frameBytes));
            carry.reset();
        }
    }

    /** Barge-in: whatever the agent still had to say is dropped. */
    void clearOutbound() {
        outbound.clear();
        carry.reset();
    }

    boolean speaking() {
        return !outbound.isEmpty();
    }

    /** Milliseconds of queued agent audio still to be sent. */
    int queuedMs() {
        return outbound.size() * FRAME_MS;
    }
}
