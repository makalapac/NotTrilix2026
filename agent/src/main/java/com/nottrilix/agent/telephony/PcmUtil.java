package com.nottrilix.agent.telephony;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Helpers for 16-bit little-endian mono PCM, the format of Infobip media streams and ElevenLabs pcm_16000. */
final class PcmUtil {

    private PcmUtil() {
    }

    /** Root-mean-square amplitude of a 16-bit LE PCM frame (0..32767). */
    static double rms(byte[] frame) {
        if (frame.length < 2) {
            return 0;
        }
        long sumSquares = 0;
        int samples = frame.length / 2;
        for (int i = 0; i < samples; i++) {
            int lo = frame[2 * i] & 0xFF;
            int hi = frame[2 * i + 1]; // signed high byte
            int sample = (hi << 8) | lo;
            sumSquares += (long) sample * sample;
        }
        return Math.sqrt((double) sumSquares / samples);
    }

    /** Wraps raw PCM16 mono in a minimal WAV container, so speech-to-text can tell the format. */
    static byte[] toWav(byte[] pcm, int sampleRate) {
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);                   // PCM chunk size
        header.putShort((short) 1);          // PCM format
        header.putShort((short) 1);          // mono
        header.putInt(sampleRate);
        header.putInt(sampleRate * 2);       // byte rate
        header.putShort((short) 2);          // block align
        header.putShort((short) 16);         // bits per sample
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(header.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    /** Splits PCM into fixed-size frames; the last partial frame is zero-padded (silence). */
    static List<byte[]> chunkFrames(byte[] pcm, int frameBytes) {
        List<byte[]> frames = new ArrayList<>(pcm.length / frameBytes + 1);
        for (int off = 0; off < pcm.length; off += frameBytes) {
            byte[] frame = new byte[frameBytes];
            System.arraycopy(pcm, off, frame, 0, Math.min(frameBytes, pcm.length - off));
            frames.add(frame);
        }
        return frames;
    }

    /** A frame of digital silence. */
    static byte[] silence(int frameBytes) {
        return new byte[frameBytes];
    }
}
