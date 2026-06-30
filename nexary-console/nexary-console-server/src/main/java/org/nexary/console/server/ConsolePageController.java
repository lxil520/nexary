package org.nexary.console.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the read-only local Nexary console shell. */
@Controller
public final class ConsolePageController {
    /** Redirects the extensionless console path so relative UI assets resolve under the console path. */
    @GetMapping("/nexary/console")
    public String redirectToConsoleDirectory() {
        return "redirect:/nexary/console/";
    }

    /** Forwards console SPA paths to the packaged static UI entry point. */
    @GetMapping({
        "/nexary/console/",
        "/nexary/console/overview",
        "/nexary/console/platform",
        "/nexary/console/topology",
        "/nexary/console/request-flows",
        "/nexary/console/incidents",
        "/nexary/console/services",
        "/nexary/console/hosts",
        "/nexary/console/middleware",
        "/nexary/console/resources",
        "/nexary/console/integrations",
        "/nexary/console/notifications",
        "/nexary/console/policies",
        "/nexary/console/local",
        "/nexary/console/local/resources",
        "/nexary/console/local/resources/{resourceKey}",
        "/nexary/console/local/events",
        "/nexary/console/local/traces",
        "/nexary/console/local/traces/{traceKey}",
        "/nexary/console/resources/{resourceKey}",
        "/nexary/console/events",
        "/nexary/console/flows",
        "/nexary/console/flows/{flowKey}",
        "/nexary/console/traces",
        "/nexary/console/traces/{traceKey}",
        "/nexary/console/settings"
    })
    public String index() {
        return "forward:/nexary/console/index.html";
    }
}
