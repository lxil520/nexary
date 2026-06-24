package org.nexary.console.server;

import org.nexary.console.api.ConsoleEventsResponse;
import org.nexary.console.api.ConsoleResourceItem;
import org.nexary.console.api.ConsoleResourcesResponse;
import org.nexary.console.api.ConsoleSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only HTTP API for local Nexary console diagnostics. */
@RestController
@RequestMapping("${nexary.console.api-path:/nexary/console/api}")
public final class ConsoleApiController {
    private final ConsoleDiagnosticsService diagnosticsService;

    /** Creates a controller backed by the console diagnostics service. */
    public ConsoleApiController(ConsoleDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    /** Returns aggregate local console diagnostics. */
    @GetMapping("/summary")
    public ConsoleSummaryResponse summary() {
        return diagnosticsService.summary();
    }

    /** Returns all known local console resources. */
    @GetMapping("/resources")
    public ConsoleResourcesResponse resources() {
        return diagnosticsService.resources();
    }

    /** Returns one local console resource by stable resource id. */
    @GetMapping("/resources/{id}")
    public ResponseEntity<ConsoleResourceItem> resource(@PathVariable("id") String id) {
        return diagnosticsService.resource(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns recent low-cardinality local console events. */
    @GetMapping("/events")
    public ConsoleEventsResponse events() {
        return diagnosticsService.events();
    }
}
