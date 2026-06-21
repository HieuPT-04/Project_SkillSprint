package com.skillsprint.configuration.websocket;

import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.community.CommunityWebSocketAccessService;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static String COMMUNITY_ROOM_TOPIC_PREFIX = "/topic/community.rooms.";

    JwtDecoder jwtDecoder;
    CommunityWebSocketAccessService communityWebSocketAccessService;

    @Value("${app.cors.allowed-origins}")
    @NonFinal
    String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(parseOrigins())
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOrigins(parseOrigins());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorization = firstHeader(accessor, "Authorization");
                    if (authorization == null) {
                        authorization = firstHeader(accessor, "authorization");
                    }
                    if (authorization != null && authorization.startsWith("Bearer ")) {
                        Jwt jwt = jwtDecoder.decode(authorization.substring(7));
                        accessor.setUser(new JwtAuthenticationToken(jwt, authorities(jwt)));
                    }
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    validateCommunityRoomSubscription(accessor);
                }
                return message;
            }
        });
    }

    private void validateCommunityRoomSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(COMMUNITY_ROOM_TOPIC_PREFIX)) {
            return;
        }

        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AppException(ErrorCode.COMMUNITY_CHAT_AUTH_REQUIRED);
        }

        String roomIdValue = destination.substring(COMMUNITY_ROOM_TOPIC_PREFIX.length());
        UUID roomId;
        try {
            roomId = UUID.fromString(roomIdValue);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.COMMUNITY_ROOM_NOT_FOUND);
        }

        String userId = principal.getName();
        communityWebSocketAccessService.validateRoomSubscription(userId, roomId);
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        return accessor.getFirstNativeHeader(name);
    }

    private String[] parseOrigins() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        return origins.toArray(String[]::new);
    }

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        Collection<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups == null) {
            groups = Collections.emptyList();
        }

        return groups.stream()
                .map(group -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + group.toUpperCase()))
                .toList();
    }
}
