package org.nexary.samples.governance.platform;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Demo helper endpoints for local platform smoke tests. */
@RestController
@RequestMapping("/demo/platform")
public final class GovernancePlatformDemoController {
    private final GovernancePlatformDemoData demoData;

    /** Creates the demo controller. */
    public GovernancePlatformDemoController(GovernancePlatformDemoData demoData) {
        this.demoData = demoData;
    }

    /** Replays the demo resource report and signals. */
    @PostMapping("/seed")
    public Map<String, Object> seed() {
        demoData.seed();
        return Map.of("seeded", true);
    }
}
