package org.nexary.console.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConsoleApiControllerTest {
    @Test
    void exposesGetOnlyConsoleApiEndpoints() throws Exception {
        GovernanceDiagnostics diagnostics = new EmptyDiagnostics();
        ConsoleApiController controller = new ConsoleApiController(new ConsoleDiagnosticsService(diagnostics));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/nexary/console/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceCount").value(0));
        mockMvc.perform(get("/nexary/console/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/nexary/console/api/resources/missing"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/nexary/console/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(post("/nexary/console/api/summary"))
                .andExpect(status().isMethodNotAllowed());
    }

    private static final class EmptyDiagnostics implements GovernanceDiagnostics {
        @Override
        public java.util.List<org.nexary.governance.runtime.GovernanceResourceDescriptor> resources() {
            return Collections.emptyList();
        }

        @Override
        public java.util.List<org.nexary.governance.runtime.GovernanceRuntimeSnapshot> snapshots() {
            return Collections.emptyList();
        }

        @Override
        public java.util.List<org.nexary.governance.runtime.GovernanceRuntimeEvent> recentEvents() {
            return Collections.emptyList();
        }

        @Override
        public GovernanceRuntimeSummary summary() {
            return new GovernanceRuntimeSummary(0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, null);
        }
    }
}
