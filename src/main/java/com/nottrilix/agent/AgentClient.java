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
 * HTTP klijent prema aplikaciji agenta (zaseban proces). Sustav agentu prosljedjuje poruku kupca (tekst ili
 * snimku) i kupcev token; agent se tim tokenom vraca u sustav kroz MCP, pa i dalje moze samo ono sto smije taj kupac.
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger("AGENT");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Poruka kupca za agenta; conversationId je opcionalan (bez njega agent pocinje novi razgovor). */
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

    /** Glasovni turn: snimka kupca (webm/ogg/mp3/wav) -> agent vraca transkript, odgovor i govor. */
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
            // agent vraca {error, message}; poruka je vec namijenjena kupcu (npr. glas nije dostupan)
            String message = messageOf(body);
            throw new AgentUnavailableException(message != null ? message
                    : "Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        } catch (RestClientException e) {
            log.error("AGENT {} nije dostupan: {}", url, e.getMessage());
            throw new AgentUnavailableException("Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        }
    }
}
