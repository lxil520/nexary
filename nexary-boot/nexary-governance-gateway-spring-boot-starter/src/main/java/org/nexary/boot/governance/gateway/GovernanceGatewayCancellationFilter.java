package org.nexary.boot.governance.gateway;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.nexary.core.context.CancellationHeaders;
import org.nexary.core.context.CancellationReason;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** Gateway filter that propagates deadlines and notifies downstream services when the client disconnects. */
public final class GovernanceGatewayCancellationFilter implements GlobalFilter, Ordered {
    private final GovernanceGatewayProperties properties;
    private final WebClient webClient;

    public GovernanceGatewayCancellationFilter(GovernanceGatewayProperties properties, WebClient webClient) {
        this.properties = properties == null ? new GovernanceGatewayProperties() : properties;
        this.webClient = webClient == null ? WebClient.builder().build() : webClient;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant receivedAt = Instant.now();
        Map<String, String> inboundHeaders = headers(exchange.getRequest().getHeaders());
        Instant deadline = CancellationHeaders.deadline(inboundHeaders, receivedAt)
                .orElse(receivedAt.plus(properties.getDefaultTimeout()));
        String cancellationId = CancellationHeaders.cancellationId(inboundHeaders)
                .orElseGet(() -> UUID.randomUUID().toString());
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(CancellationHeaders.DEADLINE_EPOCH_MILLIS, Long.toString(deadline.toEpochMilli()));
                    headers.set(CancellationHeaders.TIMEOUT_MILLIS, Long.toString(Math.max(1L, deadline.toEpochMilli() - receivedAt.toEpochMilli())));
                    headers.set(CancellationHeaders.CANCELLATION_ID, cancellationId);
                })
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        return chain.filter(mutated)
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        notifyDownstream(mutated, cancellationId, CancellationReason.CLIENT_DISCONNECTED).subscribe();
                    }
                });
    }

    private Mono<Void> notifyDownstream(
            ServerWebExchange exchange,
            String cancellationId,
            CancellationReason reason) {
        URI target = routedServiceUri(exchange);
        if (target == null || cancellationId == null) {
            return Mono.empty();
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("cancellationId", cancellationId);
        body.put("reason", reason.name());
        WebClient.RequestBodySpec request = webClient.post()
                .uri(cancelUri(target))
                .header(CancellationHeaders.CANCEL_REASON, reason.name())
                .header(CancellationHeaders.CANCELLATION_ID, cancellationId);
        if (hasText(properties.getReceiverToken())) {
            request.header(properties.getReceiverTokenHeaderName(), properties.getReceiverToken());
        }
        return request.bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(properties.getCancelNotifyTimeout())
                .onErrorResume(ignored -> Mono.empty())
                .then();
    }

    private URI cancelUri(URI target) {
        String path = properties.getCancelReceiverPath();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(target.getScheme() + "://" + target.getAuthority() + normalizedPath);
    }

    private URI routedServiceUri(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (value instanceof URI) {
            return (URI) value;
        }
        Object original = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (original instanceof URI) {
            return (URI) original;
        }
        return null;
    }

    private static Map<String, String> headers(HttpHeaders headers) {
        Map<String, String> values = new LinkedHashMap<>();
        headers.forEach((name, headerValues) -> {
            if (!headerValues.isEmpty()) {
                values.put(name, headerValues.get(0));
            }
        });
        return values;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
