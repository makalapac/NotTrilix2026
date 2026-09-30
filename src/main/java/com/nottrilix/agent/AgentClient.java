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

import java.net.http.HttpClient;
import java.time.Duration;

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

    private final RestClient http;
    private final String url;

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
        return call(() -> http.post().uri("/voice")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(VoiceTurnResponse.class));
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
