package com.nbcb.assistagent.app;

import org.junit.jupiter.api.Test;

import static com.nbcb.assistagent.app.QueryIntentRouter.QueryIntent.GENERAL;
import static com.nbcb.assistagent.app.QueryIntentRouter.QueryIntent.LOAN_CALCULATION;
import static com.nbcb.assistagent.app.QueryIntentRouter.QueryIntent.POLICY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryIntentRouterTest {

    private final QueryIntentRouter router = new QueryIntentRouter();

    @Test
    void routesLoanCalculationWithoutRag() {
        String query = "计算50万贷款，年利率4%，期限3年";

        assertEquals(LOAN_CALCULATION, router.route(query));
        assertFalse(router.shouldUseRag(query));
    }

    @Test
    void routesPolicyQuestionToRag() {
        String query = "申请授信客户需要满足哪些准入条件？";

        assertEquals(POLICY, router.route(query));
        assertTrue(router.shouldUseRag(query));
        assertEquals(POLICY, router.route("本行贷款审批有哪些要求？"));
    }

    @Test
    void routesCasualConversationWithoutRag() {
        assertEquals(GENERAL, router.route("你好，今天心情怎么样？"));
        assertEquals(GENERAL, router.route("给我讲一个简短的笑话"));
        assertFalse(router.shouldUseRag("你好"));
    }
}
