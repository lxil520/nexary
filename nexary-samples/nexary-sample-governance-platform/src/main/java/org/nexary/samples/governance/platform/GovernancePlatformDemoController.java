package org.nexary.samples.governance.platform;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Demo helper endpoints for local platform smoke tests. */
@RestController
@RequestMapping("/demo/platform")
public final class GovernancePlatformDemoController {
    private final GovernancePlatformDemoData demoData;
    private final GovernancePlatformLiveProbe liveProbe;

    /** Creates the demo controller. */
    public GovernancePlatformDemoController(GovernancePlatformDemoData demoData, GovernancePlatformLiveProbe liveProbe) {
        this.demoData = demoData;
        this.liveProbe = liveProbe;
    }

    /** Replays the demo resource report and signals. */
    @PostMapping("/seed")
    public Map<String, Object> seed() {
        demoData.seed();
        return Map.of("seeded", true);
    }

    /** Runs real Docker middleware probes and records platform signals. */
    @PostMapping("/probe")
    public Map<String, Object> probe(@RequestParam(name = "iterations", defaultValue = "25") int iterations) {
        return liveProbe.run(iterations);
    }

    /** Exposes the latest probe metrics in Prometheus text format. */
    @GetMapping(value = "/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
    public String prometheus() {
        return liveProbe.prometheusText();
    }
}
