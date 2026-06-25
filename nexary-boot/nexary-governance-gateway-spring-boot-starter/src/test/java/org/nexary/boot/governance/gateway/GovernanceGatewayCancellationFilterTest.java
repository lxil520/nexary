package org.nexary.boot.governance.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class GovernanceGatewayCancellationFilterTest {
    @Test
    void writesDeadlineAndCancellationHeadersBeforeForwarding() {
        GovernanceGatewayProperties properties = new GovernanceGatewayProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(2));
        GovernanceGatewayCancellationFilter filter = new GovernanceGatewayCancellationFilter(
                properties,
                WebClient.builder().build());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/gateway/governance/slow"));
        AtomicReference<String> cancellationId = new AtomicReference<>();
        AtomicReference<String> timeout = new AtomicReference<>();
        GatewayFilterChain chain = forwarded -> {
            cancellationId.set(forwarded.getRequest().getHeaders().getFirst(CancellationHeaders.CANCELLATION_ID));
            timeout.set(forwarded.getRequest().getHeaders().getFirst(CancellationHeaders.TIMEOUT_MILLIS));
            assertThat(forwarded.getRequest().getHeaders().getFirst(CancellationHeaders.DEADLINE_EPOCH_MILLIS))
                    .isNotBlank();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(cancellationId.get()).isNotBlank();
        assertThat(Long.parseLong(timeout.get())).isPositive();
    }

    @Test
    void notifiesDownstreamReceiverWhenReactiveChainIsCancelled() throws Exception {
        GovernanceGatewayProperties properties = new GovernanceGatewayProperties();
        properties.setReceiverToken("receiver-token");
        AtomicReference<ClientRequest> notified = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    notified.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                })
                .build();
        GovernanceGatewayCancellationFilter filter = new GovernanceGatewayCancellationFilter(properties, webClient);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/gateway/governance/slow"));
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR,
                URI.create("http://downstream.example/governance/slow"));

        Disposable subscription = filter.filter(exchange, ignored -> Mono.never()).subscribe();
        subscription.dispose();
        Thread.sleep(50);

        assertThat(notified.get()).isNotNull();
        assertThat(notified.get().url().toString())
                .isEqualTo("http://downstream.example/nexary/governance/cancellations");
        assertThat(notified.get().headers().getFirst(CancellationHeaders.CANCEL_REASON))
                .isEqualTo("CLIENT_DISCONNECTED");
        assertThat(notified.get().headers().getFirst(properties.getReceiverTokenHeaderName()))
                .isEqualTo("receiver-token");
    }
}
