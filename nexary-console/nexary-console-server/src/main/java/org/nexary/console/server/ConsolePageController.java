package org.nexary.console.server;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the read-only local Nexary console shell. */
@Controller
public final class ConsolePageController {
    /** Forwards the console root to the packaged static UI entry point. */
    @GetMapping({"/nexary/console", "/nexary/console/"})
    public String index() {
        return "forward:/nexary/console/index.html";
    }
}
