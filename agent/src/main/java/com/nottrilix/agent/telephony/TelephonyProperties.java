package com.nottrilix.agent.telephony;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phone channel over Infobip (application.yml -> nottrilix.telephony.*).
 * baseUrl / apiKey: the Infobip account (infobip.key: line 1 = key, line 2 = base URL; loaded by run-phase-6.ps1).
 * websocketEndpointConfigId: Infobip media-stream config pointing at our public wss://.../infobip/media; re-pointed at
 * startup when publicUrl is set (the tunnel URL changes with every start).
 * systemUrl: the system's /api/phone endpoints (caller identity, turns, end of call).
 * greeting: what Nola says when the call is connected ({name} = the recognised customer's first name, or nothing).
 * vad: energy-based voice activity detection on 16-bit PCM (RMS 0..32767).
 */
@ConfigurationProperties(prefix = "nottrilix.telephony")
public record TelephonyProperties(boolean enabled, String baseUrl, String apiKey, String websocketEndpointConfigId,
                                  String publicUrl, String systemUrl, int sampleRate, String greeting,
                                  String greetingUnknown, String fillerText, int fillerAfterMs, int maxCallSeconds,
                                  Vad vad) {

    public record Vad(double speechRms, int minSpeechMs, int endSilenceMs, int maxUtteranceMs) {
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
