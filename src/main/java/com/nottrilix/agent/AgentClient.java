package com.nottrilix.agent;

import com.nottrilix.api.dto.AgentChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP klijent prema aplikaciji agenta (zaseban proces). Sustav agentu prosljedjuje poruku kupca i kupcev token;
 * agent se tim tokenom vraca u sustav kroz MCP, pa i dalje moze samo ono sto smije taj kupac.
 */
@Component
public class AgentClient {

    private static final Logger log = LoggerFactory.getLogger("AGENT");

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
        try {
            AgentChatResponse response = http.post().uri("/chat")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AgentChatResponse.class);
            if (response == null) {
                throw new AgentUnavailableException("Agent je vratio prazan odgovor");
            }
            return response;
        } catch (RestClientResponseException e) {
            log.error("AGENT {} -> HTTP {} {}", url, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AgentUnavailableException("Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        } catch (RestClientException e) {
            log.error("AGENT {} nije dostupan: {}", url, e.getMessage());
            throw new AgentUnavailableException("Agent trenutno nije dostupan. Pokušajte ponovno za koji trenutak.");
        }
    }
}
