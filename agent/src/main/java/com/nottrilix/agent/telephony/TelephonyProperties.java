package com.nottrilix.agent.telephony;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Phone channel over Infobip (application.yml -> nottrilix.telephony.*).
 * baseUrl / apiKey: the Infobip account (infobip.key: line 1 = key, line 2 = base URL; loaded by run-phase-6.ps1).
 * websocketEndpointConfigId: Infobip media-stream config pointing at our public wss://.../infobip/media; re-pointed at
 * startup when publicUrl is set (the tunnel URL changes with every start).
 * systemUrl: the system's /api/phone endpoints (caller identity, turns, end of call).
 * greeting: what Nola says when the call is connected ({name} = the recognised customer's first name, or nothing).
 * fillerTexts: short phrases Nola says, in rotation, when the agent takes longer than fillerAfterMs (the same phrase
 * on every turn sounds like a machine).
 * sttModel: transcription model for the phone line only (empty = the Spring AI default shared with the browser voice);
 * the line is narrow-band and noisier, so a stronger model can pay off here.
 * vad: energy-based voice activity detection on 16-bit PCM (RMS 0..32767); bargeInRms is the louder threshold that
 * interrupts Nola while she speaks (the echo of her own voice on a speakerphone must stay below it).
 */
@ConfigurationProperties(prefix = "nottrilix.telephony")
public record TelephonyProperties(boolean enabled, String baseUrl, String apiKey, String websocketEndpointConfigId,
                                  String publicUrl, String systemUrl, int sampleRate, String greeting,
                                  String greetingUnknown, List<String> fillerTexts, int fillerAfterMs, int maxCallSeconds,
                                  String sttModel, Vad vad) {

    public record Vad(double speechRms, int minSpeechMs, int endSilenceMs, int maxUtteranceMs, double bargeInRms) {
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
                && !"not-set".equals(apiKey);
    }

    /** wss://<public host>/infobip/media, or null when there is no public URL. */
    public String mediaWebSocketUrl() {
        if (publicUrl == null || publicUrl.isBlank()) {
            return null;
        }
        String base = publicUrl.trim().replaceFirst("/+$", "");
        if (base.startsWith("https://")) {
            base = "wss://" + base.substring("https://".length());
        } else if (base.startsWith("http://")) {
            base = "ws://" + base.substring("http://".length());
        }
        return base + "/infobip/media";
    }
}
