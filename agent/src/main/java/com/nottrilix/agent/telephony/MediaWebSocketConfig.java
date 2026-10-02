package com.nottrilix.agent.telephony;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Infobip dials wss://<public host>/infobip/media for every bridged call (server to server, no browser origin). */
@Configuration
@EnableWebSocket
class MediaWebSocketConfig implements WebSocketConfigurer {

    private final InfobipMediaWebSocketHandler handler;

    MediaWebSocketConfig(InfobipMediaWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/infobip/media").setAllowedOrigins("*");
    }
}
