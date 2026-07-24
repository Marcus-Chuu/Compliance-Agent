package com.nbcb.assistagent.tool;

import com.nbcb.assistagent.rag.index.RagIndexingProperties;
import com.nbcb.assistagent.tools.AssistantTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantToolsTest {

    @Test
    void calculatesEqualPrincipalAndInterestLoan() {
        AssistantTools tools = newTools();

        AssistantTools.LoanCalculation result = tools.calculateLoan(
                new BigDecimal("100000"), new BigDecimal("4.2"), 1);

        assertEquals(new BigDecimal("8524.13"), result.monthlyPayment());
        assertEquals(new BigDecimal("102289.57"), result.totalRepayment());
        assertEquals(new BigDecimal("2289.57"), result.totalInterest());
        assertEquals(12, result.months());
    }

    @Test
    void calculatesZeroInterestLoan() {
        AssistantTools tools = newTools();

        AssistantTools.LoanCalculation result = tools.calculateLoan(
                new BigDecimal("12000"), BigDecimal.ZERO, 1);

        assertEquals(new BigDecimal("1000.00"), result.monthlyPayment());
        assertEquals(new BigDecimal("0.00"), result.totalInterest());
    }

    @Test
    void rejectsInvalidLoanArguments() {
        AssistantTools tools = newTools();

        assertThrows(IllegalArgumentException.class,
                () -> tools.calculateLoan(BigDecimal.ZERO, new BigDecimal("4.2"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> tools.calculateLoan(new BigDecimal("100000"), new BigDecimal("-1"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> tools.calculateLoan(new BigDecimal("100000"), new BigDecimal("4.2"), 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesPolicyByLiteralKeywordWithoutVectorSimilarity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RagIndexingProperties indexingProperties = mock(RagIndexingProperties.class);
        when(indexingProperties.getCollection()).thenReturn("assist-documents");
        AssistantTools.PolicyClause clause = new AssistantTools.PolicyClause(
                "授信管理办法",
                "客户准入",
                "条款101",
                "准入条件",
                "credit-policy.md",
                "客户信用状况良好。",
                "ARTICLE_NUMBER_EXACT");
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<AssistantTools.PolicyClause>>any(),
                eq("条款101"),
                eq("assist-documents")))
                .thenReturn(List.of(clause));
        AssistantTools tools = new AssistantTools(jdbcTemplate, indexingProperties);

        List<AssistantTools.PolicyClause> results = tools.searchPolicy("条款101");

        assertEquals(1, results.size());
        assertEquals("条款101", results.getFirst().articleNumber());
        assertEquals("credit-policy.md", results.getFirst().source());
        assertEquals("ARTICLE_NUMBER_EXACT", results.getFirst().matchType());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<AssistantTools.PolicyClause>>any(),
                eq("条款101"),
                eq("assist-documents"));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("strpos"));
        assertFalse(sql.contains("<=>"));
        assertFalse(sql.contains("embedding"));
    }

    private AssistantTools newTools() {
        return new AssistantTools(mock(JdbcTemplate.class), mock(RagIndexingProperties.class));
    }
}
