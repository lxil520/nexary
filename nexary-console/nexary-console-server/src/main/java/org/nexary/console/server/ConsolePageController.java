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

    /** Forwards the console directory path to the packaged static UI entry point. */
    @GetMapping("/nexary/console/")
    public String index() {
        return "forward:/nexary/console/index.html";
    }
}
