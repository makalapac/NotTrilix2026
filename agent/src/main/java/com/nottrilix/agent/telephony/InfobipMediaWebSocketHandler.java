package com.nottrilix.agent.telephony;

import com.nottrilix.agent.AgentChatResponse;
import com.nottrilix.agent.AgentService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The media plane of a phone call. Infobip bridges the answered call here as a WebSocket: the first message is JSON
 * metadata, then 20 ms frames of 16-bit PCM flow in both directions. This is the phone-shaped twin of the browser voice
 * turn: the caller's utterances (cut by the VAD) go to speech-to-text, the text goes to the same AgentService with the
 * customer token the system issued for this call, the reply comes back as PCM and is sent at real-time pace. The caller
 * can interrupt Nola (barge-in); nothing of the caller's audio is stored.
 */
@Component
class InfobipMediaWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger("TELEPHONY");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CHANNEL = "PHONE";
    private static final int FRAME_MS = 20;

    /** Everything one bridged WebSocket carries. */
    private static final class Media {
        final WebSocketSession ws;
        final PhoneCall call;
        final CallSession session;
        volatile ScheduledFuture<?> ticker;
        volatile boolean busy;
        volatile boolean hangupWhenDone;
        volatile boolean hungUp;

        Media(WebSocketSession ws, PhoneCall call, CallSession session) {
            this.ws = ws;
            this.call = call;
            this.session = session;
        }
    }

    private final AgentService agent;
    private final TelephonySpeech speech;
    private final CallRegistry registry;
    private final PhoneGateway gateway;
    private final InfobipCallsClient infobip;
    private final TelephonyProperties props;
    private final Clock clock;
    private final Map<String, Media> media = new ConcurrentHashMap<>();
    private final ExecutorService pipeline = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService pacer = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "telephony-pacer");
        thread.setDaemon(true);
        return thread;
    });

    InfobipMediaWebSocketHandler(AgentService agent, TelephonySpeech speech, CallRegistry registry,
                                 PhoneGateway gateway, InfobipCallsClient infobip, TelephonyProperties props,
                                 Clock clock) {
        this.agent = agent;
        this.speech = speech;
        this.registry = registry;
        this.gateway = gateway;
        this.infobip = infobip;
        this.props = props;
        this.clock = clock;
    }

    /** First message: {"callId": "...", "sampleRate": 16000, ...}. */
    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        JsonNode meta;
        try {
            meta = JSON.readTree(message.getPayload());
        } catch (RuntimeException e) {
            log.warn("TELEPHONY nerazumljiva metadata poruka na {}: {}", ws.getId(), message.getPayload());
            return;
        }
        String callId = meta.path("callId").asString(null);
        int sampleRate = meta.path("sampleRate").asInt(props.sampleRate());
        // the stream may report the bridged leg's id rather than the call we answered
        PhoneCall call = registry.get(callId)
                .or(registry::newestWithoutMedia)
                .orElseGet(() -> registry.register(callId == null ? ws.getId() : callId, "?"));
        call.mediaAttached = true;
        if (sampleRate != props.sampleRate()) {
            log.warn("CALL {} media stream {} Hz, a govor se sintetizira na {} Hz: uskladi media-stream config",
                    call.callId, sampleRate, props.sampleRate());
        }
        Media m = new Media(ws, call, new CallSession(call.callId, sampleRate, props.vad()));
        media.put(ws.getId(), m);
        m.ticker = pacer.scheduleAtFixedRate(() -> tick(m), 0, FRAME_MS, TimeUnit.MILLISECONDS);
        log.info("CALL {} media stream otvoren ({} Hz, {} B/okvir), pozivatelj {}", call.callId, sampleRate,
                m.session.frameBytes, call.identity.identified() ? "customer#" + call.identity.customerId()
                        : "nepoznat");

        pipeline.submit(() -> {
            try {
                if (!speech.available()) {
                    log.error("CALL {} govor nije dostupan (STT/TTS), prekidam", call.callId);
                    m.hangupWhenDone = true;
                    return;
                }
                if (call.identity.identified()) {
                    speech.greetingStreaming(call.firstName(), m.session::appendAudio);
                    m.session.flushAudio();
                } else {
                    m.session.enqueueAudio(speech.greetingUnknown());
                    m.hangupWhenDone = true;
                }
            } catch (RuntimeException e) {
                log.error("CALL {} pozdrav nije uspio: {}", call.callId, e.toString());
            }
        });
    }

    /** Caller audio, one 20 ms frame at a time. */
    @Override
    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
        Media m = media.get(ws.getId());
        if (m == null) {
            return; // no metadata yet
        }
        ByteBuffer buffer = message.getPayload();
        byte[] frame = new byte[buffer.remaining()];
        buffer.get(frame);
        CallSession.FeedResult result = m.session.feed(frame);
        if (result.speechStarted() && m.session.speaking() && !m.hangupWhenDone) {
            log.info("CALL {} upad: pozivatelj govori, prekidam Nolu ({} ms neizgovoreno)", m.call.callId,
                    m.session.queuedMs());
            m.session.clearOutbound();
        }
        if (result.utterance() != null && !m.hangupWhenDone) {
            if (m.busy) {
                log.info("CALL {} iskaz preskočen: prethodni turn još traje", m.call.callId);
                return;
            }
            m.busy = true;
            byte[] utterance = result.utterance();
            pipeline.submit(() -> turn(m, utterance));
        }
    }

    /** One utterance -> text -> the same agent (customer token from the system) -> speech. */
    private void turn(Media m, byte[] pcm) {
        PhoneCall call = m.call;
        long start = System.nanoTime();
        try {
            String text = speech.transcribe(pcm, m.session.sampleRate);
            long sttMs = (System.nanoTime() - start) / 1_000_000;
            if (text.isBlank()) {
                log.info("CALL {} iskaz bez teksta ({} ms zvuka)", call.callId, pcm.length / (m.session.frameBytes / FRAME_MS));
                return;
            }
            log.info("CALL {} pozivatelj ({} ms, STT {} ms): '{}'", call.callId,
                    pcm.length * FRAME_MS / m.session.frameBytes, sttMs, text);
            if (!call.identity.identified()) {
                m.session.enqueueAudio(speech.greetingUnknown());
                m.hangupWhenDone = true;
                return;
            }
            ScheduledFuture<?> filler = pacer.schedule(() -> {
                if (!m.session.speaking()) {
                    m.session.enqueueAudio(speech.filler());
                }
            }, props.fillerAfterMs(), TimeUnit.MILLISECONDS);
            AgentChatResponse chat;
            try {
                chat = agent.chat(call.identity.customerToken(), call.callId, text, CHANNEL);
            } finally {
                filler.cancel(false);
            }
            long agentMs = (System.nanoTime() - start) / 1_000_000 - sttMs;
            long[] firstChunkMs = {-1};
            int replyBytes = speech.speakStreaming(chat.reply(), chunk -> {
                if (firstChunkMs[0] < 0) {
                    firstChunkMs[0] = (System.nanoTime() - start) / 1_000_000 - sttMs - agentMs;
                }
                m.session.appendAudio(chunk);
            });
            m.session.flushAudio();
            long totalMs = (System.nanoTime() - start) / 1_000_000;
            call.turns.incrementAndGet();
            log.info("CALL {} Nola (agent {} ms, TTS prvi zvuk {} ms / ukupno {} ms, {} ms govora): '{}'", call.callId,
                    agentMs, firstChunkMs[0], totalMs - sttMs - agentMs, replyBytes * FRAME_MS / m.session.frameBytes,
                    chat.reply());
            gateway.recordTurn(call, text, chat.reply(), totalMs, chat.promptTokens(), chat.completionTokens());
        } catch (RuntimeException e) {
            log.error("CALL {} turn nije uspio: {}", call.callId, e.toString());
            try {
                m.session.enqueueAudio(speech.speak("Nažalost, trenutno ne mogu odgovoriti. Pokušajte ponovno."));
            } catch (RuntimeException ignored) {
                // nothing more to say
            }
        } finally {
            m.busy = false;
        }
    }

    /** Every 20 ms: one queued frame to the caller; silence otherwise. Ends the call when it is time. */
    private void tick(Media m) {
        try {
            byte[] frame = m.session.outbound.poll();
            if (frame != null) {
                m.ws.sendMessage(new BinaryMessage(frame));
                return;
            }
            boolean tooLong = Duration.between(m.call.startedAt, Instant.now(clock)).toSeconds() > props.maxCallSeconds();
            if ((m.hangupWhenDone && !m.busy) || tooLong) {
                hangup(m, tooLong ? "max-duration" : "done");
            }
        } catch (Exception e) {
            log.warn("CALL {} slanje okvira nije uspjelo ({}), zatvaram", m.call.callId, e.getMessage());
            closeQuietly(m);
        }
    }

    private void hangup(Media m, String reason) {
        if (m.hungUp) {
            return;
        }
        m.hungUp = true;
        log.info("CALL {} kraj ({})", m.call.callId, reason);
        if (infobip.configured()) {
            try {
                infobip.hangup(m.call.callId);
                return;
            } catch (RuntimeException e) {
                log.warn("CALL {} hangup preko Infobipa nije uspio: {}", m.call.callId, e.getMessage());
            }
        }
        closeQuietly(m);
    }

    private static void closeQuietly(Media m) {
        try {
            m.ws.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
            // already closed
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        Media m = media.remove(ws.getId());
        if (m == null) {
            return;
        }
        if (m.ticker != null) {
            m.ticker.cancel(false);
        }
        PhoneCall call = m.call;
        log.info("CALL {} media stream zatvoren ({}), {} turnova", call.callId, status, call.turns.get());
        if (!call.ended) {
            call.ended = true;
            gateway.end(call, "media-closed", Duration.between(call.startedAt, Instant.now(clock)).toMillis());
            registry.remove(call.callId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        log.warn("TELEPHONY greška prijenosa na {}: {}", ws.getId(), exception.getMessage());
    }

    @PreDestroy
    void shutdown() {
        pipeline.shutdownNow();
        pacer.shutdownNow();
    }
}
