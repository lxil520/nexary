package org.nexary.boot.governance;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationHeaders;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.context.CancellationToken;
import org.nexary.core.context.DeadlineContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auto-configuration for cooperative request cancellation in servlet applications. */
@AutoConfiguration(after = GovernanceRuntimeAutoConfiguration.class)
@EnableConfigurationProperties(GovernanceRuntimeProperties.class)
@ConditionalOnClass({Filter.class, RestController.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GovernanceCancellationAutoConfiguration {
    /** Registers inbound header binding for deadline and cancellation token propagation. */
    @Bean
    @ConditionalOnProperty(
            prefix = "nexary.governance.cancellation.inbound",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<CancellationInboundFilter> nexaryCancellationInboundFilter() {
        FilterRegistrationBean<CancellationInboundFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CancellationInboundFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /** Servlet filter that binds propagated cancellation headers to the request thread. */
    public static final class CancellationInboundFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (!(request instanceof HttpServletRequest)) {
                chain.doFilter(request, response);
                return;
            }
            Map<String, String> headers = headers((HttpServletRequest) request);
            Optional<Instant> deadline = CancellationHeaders.deadline(headers, Instant.now());
            Optional<String> cancellationId = CancellationHeaders.cancellationId(headers);
            CancellationReason reason = CancellationHeaders.cancellationReason(headers);
            CancellationToken token = cancellationId.map(CancellationToken::create).orElse(null);
            if (token != null && reason != CancellationReason.NONE) {
                token.cancel(reason);
            }
            runScoped(deadline.orElse(null), token, () -> {
                chain.doFilter(request, response);
                return null;
            });
        }

        private static void runScoped(Instant deadline, CancellationToken token, Callable<Void> action)
                throws IOException, ServletException {
            try {
                if (deadline != null && token != null) {
                    DeadlineContext.callWithDeadline(deadline, () -> CancellationContext.callWithToken(token, action));
                } else if (deadline != null) {
                    DeadlineContext.callWithDeadline(deadline, action);
                } else if (token != null) {
                    CancellationContext.callWithToken(token, action);
                } else {
                    action.call();
                }
            } catch (IOException ex) {
                throw ex;
            } catch (ServletException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ServletException(ex);
            }
        }

        private static Map<String, String> headers(HttpServletRequest request) {
            Map<String, String> headers = new LinkedHashMap<>();
            Enumeration<String> names = request.getHeaderNames();
            while (names != null && names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name, request.getHeader(name));
            }
            return headers;
        }
    }

    /** Read-only receiver for downstream cancellation notifications. */
    @RestController
    @ConditionalOnProperty(
            prefix = "nexary.governance.cancellation.receiver",
            name = "enabled",
            havingValue = "true")
    @RequestMapping("${nexary.governance.cancellation.receiver.path-prefix:/nexary/governance}")
    public static final class CancellationReceiverEndpoint {
        private final GovernanceRuntimeProperties properties;

        public CancellationReceiverEndpoint(GovernanceRuntimeProperties properties) {
            this.properties = properties;
        }

        /** Cancels a local token registered by the inbound filter. */
        @PostMapping("/cancellations")
        public ResponseEntity<Map<String, Object>> cancel(
                @RequestHeader Map<String, String> headers,
                @RequestBody(required = false) Map<String, String> body) {
            GovernanceRuntimeProperties.Receiver receiver = properties.getCancellation().getReceiver();
            if (!authorized(receiver, headers)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response(false, CancellationReason.NONE));
            }
            String cancellationId = value(body, "cancellationId")
                    .orElseGet(() -> header(headers, CancellationHeaders.CANCELLATION_ID).orElse(null));
            CancellationReason reason = value(body, "reason")
                    .map(CancellationReceiverEndpoint::reason)
                    .orElseGet(() -> CancellationHeaders.cancellationReason(headers));
            if (reason == CancellationReason.NONE) {
                reason = CancellationReason.MANUAL;
            }
            boolean cancelled = CancellationContext.cancel(cancellationId, reason);
            return ResponseEntity.ok(response(cancelled, reason));
        }

        private static boolean authorized(GovernanceRuntimeProperties.Receiver receiver, Map<String, String> headers) {
            String token = receiver.getToken();
            if (!hasText(token)) {
                return true;
            }
            return header(headers, receiver.getTokenHeaderName()).map(token::equals).orElse(false);
        }

        private static Map<String, Object> response(boolean cancelled, CancellationReason reason) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("cancelled", cancelled);
            body.put("reason", reason == null ? CancellationReason.NONE.name() : reason.name());
            return body;
        }

        private static Optional<String> value(Map<String, String> body, String key) {
            return body == null ? Optional.empty() : Optional.ofNullable(body.get(key)).filter(GovernanceCancellationAutoConfiguration::hasText);
        }

        private static Optional<String> header(Map<String, String> headers, String name) {
            if (headers == null || name == null) {
                return Optional.empty();
            }
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    return Optional.ofNullable(entry.getValue()).filter(GovernanceCancellationAutoConfiguration::hasText);
                }
            }
            return Optional.empty();
        }

        private static CancellationReason reason(String value) {
            try {
                return CancellationReason.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (RuntimeException ex) {
                return CancellationReason.NONE;
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
