package com.nbcb.assistagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "assist.rag.indexing.enabled=false")
class AssistAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
