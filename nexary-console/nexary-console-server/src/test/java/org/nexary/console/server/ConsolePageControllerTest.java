package org.nexary.console.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConsolePageControllerTest {
    private static final String CONSOLE_INDEX_FORWARD = "/nexary/console/index.html";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConsolePageController()).build();

    @Test
    void redirectsConsoleRootToDirectoryPath() throws Exception {
        mockMvc.perform(get("/nexary/console"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/nexary/console/"));
    }

    @Test
    void forwardsConsoleDirectoryPathToPackagedStaticIndex() throws Exception {
        mockMvc.perform(get("/nexary/console/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(CONSOLE_INDEX_FORWARD));
    }

    @Test
    void forwardsConsoleSpaPathsToPackagedStaticIndex() throws Exception {
        String[] paths = {
            "/nexary/console/overview",
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
            "/nexary/console/local/resources/cacheOrders",
            "/nexary/console/local/resources/cache:orders",
            "/nexary/console/local/events",
            "/nexary/console/local/traces",
            "/nexary/console/local/traces/trace-1",
            "/nexary/console/resources/cacheOrders",
            "/nexary/console/resources/cache:orders",
            "/nexary/console/events",
            "/nexary/console/settings"
        };

        for (String path : paths) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl(CONSOLE_INDEX_FORWARD));
        }
    }
}
