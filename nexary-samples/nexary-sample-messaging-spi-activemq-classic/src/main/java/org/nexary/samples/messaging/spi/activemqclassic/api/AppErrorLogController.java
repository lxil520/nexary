package org.nexary.samples.messaging.spi.activemqclassic.api;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.nexary.samples.messaging.spi.activemqclassic.facade.AppErrorLogFacade;
import org.nexary.samples.messaging.spi.activemqclassic.domain.MessagingSampleInbox;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Provider-backed messaging sample endpoints. */
@RestController
@RequestMapping("/app-error-logs")
public class AppErrorLogController {
    private final AppErrorLogFacade facade;

    public AppErrorLogController(AppErrorLogFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public AppErrorLogFacade.PublishResponse publish(@RequestBody PublishRequest request)
            throws ExecutionException, InterruptedException {
        return facade.publish(new AppErrorLogFacade.PublishCommand(
                request.appId(),
                request.messageId(),
                request.level(),
                request.message()));
    }

    @GetMapping
    public MessagingSampleInbox.Snapshot messages() {
        return facade.snapshot();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("message", ex.getMessage());
    }

    /** Publish request body. */
    public record PublishRequest(String appId, String messageId, String level, String message) {
    }
}
