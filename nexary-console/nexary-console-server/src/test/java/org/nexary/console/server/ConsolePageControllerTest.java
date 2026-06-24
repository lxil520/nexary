package org.nexary.console.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConsolePageControllerTest {
    @Test
    void forwardsConsoleRootToPackagedStaticIndex() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConsolePageController()).build();

        mockMvc.perform(get("/nexary/console"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/nexary/console/index.html"));
        mockMvc.perform(get("/nexary/console/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/nexary/console/index.html"));
    }
}
