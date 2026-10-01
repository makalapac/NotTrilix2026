package com.nottrilix.agent;

import com.nottrilix.api.dto.AgentChatResponse;
import com.nottrilix.api.dto.VoiceTurnResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP client towards the agent application (a separate process). The system forwards the customer's message (text or
 * recording) and the customer's token to the agent; the agent comes back into the system via MCP with that token, so it can still only do what that customer may.
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger("AGENT");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The customer's message for the agent; conversationId is optional (without it the agent starts a new conversation). */
    public record TurnRequest(String conversationId, String message, String channel) {
    }

    private static final String UNAVAILABLE = "Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.";

    /** What the streaming voice turn reports before (or instead of) the audio. */
    public interface VoiceStreamListener {
        void transcript(String transcript, long sttMs);

        void chat(AgentChatResponse chat, String ttsProvider);

        void error(String error, String message);
    }

    private final RestClient http;
    private final String url;
    private volatile byte[] filler;

    public AgentClient(AgentClientProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()))
                .build());
        factory.setReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds()));
        this.url = props.url();
        this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public AgentChatResponse chat(String authorization, TurnRequest request) {
        return call(() -> http.post().uri("/chat")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AgentChatResponse.class));
    }

    /** Voice turn: the customer's recording (webm/ogg/mp3/wav) -> the agent returns the transcript, the reply and speech. */
    public VoiceTurnResponse voice(String authorization, String conversationId, byte[] audio, String filename,
                                   String contentType) {
        MultiValueMap<String, Object> form = form(conversationId, audio, filename, contentType);
        return call(() -> http.post().uri("/voice")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(VoiceTurnResponse.class));
    }

    /**
     * Streaming voice turn: the agent answers with JSON event lines (transcript, chat, audio header) followed by raw
     * mp3 bytes; everything is forwarded to {@code out} as it arrives and the listener sees the events on the way.
     * Failures become an error line on the stream, because the HTTP status is already committed.
     */
    public void voiceStream(String authorization, String conversationId, byte[] audio, String filename,
                            String contentType, OutputStream out, VoiceStreamListener listener) throws IOException {
        MultiValueMap<String, Object> form = form(conversationId, audio, filename, contentType);
        try {
            http.post().uri("/voice/stream")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            log.error("AGENT {} /voice/stream -> HTTP {} {}", url, response.getStatusCode().value(), body);
                            String message = messageOf(body);
                            fail(out, listener, "AGENT_UNAVAILABLE", message != null ? message : UNAVAILABLE);
                            return null;
                        }
                        forward(new BufferedInputStream(response.getBody()), out, listener);
                        return null;
                    });
        } catch (RestClientException e) {
            log.error("AGENT {} nije dostupan: {}", url, e.getMessage());
            fail(out, listener, "AGENT_UNAVAILABLE", UNAVAILABLE);
        }
    }

    /** Nola's bridging phrase (mp3), fetched from the agent once per process. */
    public byte[] voiceFiller() {
        byte[] cached = filler;
        if (cached == null) {
            cached = call(() -> http.get().uri("/voice/filler").retrieve().body(byte[].class));
            filler = cached;
        }
        return cached;
    }

    private static MultiValueMap<String, Object> form(String conversationId, byte[] audio, String filename,
                                                      String contentType) {
        ByteArrayResource file = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("audio", file);
        if (conversationId != null && !conversationId.isBlank()) {
            form.add("conversationId", conversationId);
        }
        form.add("audioContentType", contentType == null ? "" : contentType);
        return form;
    }

    /** Header lines pass through one by one (and reach the listener); after the audio line the bytes are copied as they come. */
    private static void forward(InputStream in, OutputStream out, VoiceStreamListener listener) throws IOException {
        boolean audio = false;
        String line;
        while (!audio && (line = readLine(in)) != null) {
            JsonNode event = JSON.readTree(line);
            String type = event.path("type").asString("");
            switch (type) {
                case "transcript" -> listener.transcript(event.path("transcript").asString(""),
                        event.path("sttMs").asLong(0));
                case "chat" -> listener.chat(JSON.treeToValue(event.get("chat"), AgentChatResponse.class),
                        event.path("ttsProvider").asString(""));
                case "error" -> listener.error(event.path("error").asString("AGENT_ERROR"),
                        event.path("message").asString(""));
                case "audio" -> audio = true;
                default -> {
                    // unknown event: forwarded as is
                }
            }
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        }
        byte[] buffer = new byte[4096];
        int n;
        while (audio && (n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            out.flush();
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            line.write(b);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
    }

    private static void fail(OutputStream out, VoiceStreamListener listener, String error, String message)
            throws IOException {
        listener.error(error, message);
        out.write(JSON.writeValueAsString(Map.of("type", "error", "error", error, "message", message))
                .getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }

    private static String messageOf(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            String message = node.path("message").asString(null);
            return message == null || message.isBlank() ? null : message;
        } catch (JacksonException e) {
            return null;
        }
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            T response = request.get();
            if (response == null) {
                throw new AgentUnavailableException("Agent je vratio prazan odgovor");
            }
            return response;
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            log.error("AGENT {} -> HTTP {} {}", url, e.getStatusCode().value(), body);
            // the agent returns {error, message}; the message is already meant for the customer (e.g. voice is unavailable)
            String message = messageOf(body);
            throw new AgentUnavailableException(message != null ? message
                    : "Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        } catch (RestClientException e) {
            log.error("AGENT {} nije dostupan: {}", url, e.getMessage());
            throw new AgentUnavailableException("Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        }
    }
}
