package com.nbcb.assistagent.tools;

import com.nbcb.assistagent.rag.index.RagIndexingProperties;
import com.nbcb.assistagent.rag.pgdata.PgVectorStoreConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

@Component
public class AssistantTools {

    private static final int POLICY_TOP_K = 5;

    private static final int MAX_POLICY_KEYWORD_LENGTH = 200;

    private static final MathContext CALCULATION_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

    private static final String POLICY_SEARCH_SQL = """
            WITH params AS (
                SELECT lower(CAST(? AS text)) AS keyword, CAST(? AS text) AS collection
            )
            SELECT
                metadata ->> 'documentTitle' AS document_title,
                metadata ->> 'chapter' AS chapter,
                metadata ->> 'articleNumber' AS article_number,
                metadata ->> 'articleTitle' AS article_title,
                metadata ->> 'source' AS source,
                content,
                CASE
                    WHEN lower(COALESCE(metadata ->> 'articleNumber', '')) = params.keyword
                        THEN 'ARTICLE_NUMBER_EXACT'
                    WHEN strpos(lower(COALESCE(metadata ->> 'articleNumber', '')), params.keyword) > 0
                        THEN 'ARTICLE_NUMBER'
                    WHEN strpos(lower(COALESCE(metadata ->> 'articleTitle', '')), params.keyword) > 0
                        THEN 'ARTICLE_TITLE'
                    WHEN strpos(lower(COALESCE(metadata ->> 'chapter', '')), params.keyword) > 0
                        THEN 'CHAPTER'
                    WHEN strpos(lower(COALESCE(metadata ->> 'documentTitle', '')), params.keyword) > 0
                        THEN 'DOCUMENT_TITLE'
                    ELSE 'CONTENT'
                END AS match_type
            FROM %s.%s
            CROSS JOIN params
            WHERE metadata ->> 'collection' = params.collection
              AND (
                    lower(COALESCE(metadata ->> 'articleNumber', '')) = params.keyword
                 OR strpos(lower(COALESCE(metadata ->> 'articleNumber', '')), params.keyword) > 0
                 OR strpos(lower(COALESCE(metadata ->> 'articleTitle', '')), params.keyword) > 0
                 OR strpos(lower(COALESCE(metadata ->> 'chapter', '')), params.keyword) > 0
                 OR strpos(lower(COALESCE(metadata ->> 'documentTitle', '')), params.keyword) > 0
                 OR strpos(lower(COALESCE(content, '')), params.keyword) > 0
              )
            ORDER BY
                CASE
                    WHEN lower(COALESCE(metadata ->> 'articleNumber', '')) = params.keyword THEN 1
                    WHEN strpos(lower(COALESCE(metadata ->> 'articleNumber', '')), params.keyword) > 0 THEN 2
                    WHEN strpos(lower(COALESCE(metadata ->> 'articleTitle', '')), params.keyword) > 0 THEN 3
                    WHEN strpos(lower(COALESCE(metadata ->> 'chapter', '')), params.keyword) > 0 THEN 4
                    WHEN strpos(lower(COALESCE(metadata ->> 'documentTitle', '')), params.keyword) > 0 THEN 5
                    ELSE 6
                END,
                metadata ->> 'source',
                metadata ->> 'articleNumber',
                COALESCE((metadata ->> 'chunkIndex')::integer, 0)
            LIMIT %d
            """.formatted(
            PgVectorStoreConfig.VECTOR_SCHEMA,
            PgVectorStoreConfig.VECTOR_TABLE,
            POLICY_TOP_K);

    private final JdbcTemplate jdbcTemplate;

    private final RagIndexingProperties indexingProperties;

    public AssistantTools(JdbcTemplate jdbcTemplate, RagIndexingProperties indexingProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.indexingProperties = indexingProperties;
    }

    @Tool(
            name = "search_policy",
            description = "使用原文关键词匹配内部制度条款，不进行向量语义检索。适合精确查询条款编号、专有名词、代码或容易被语义混淆的文本；query 应传入要精确匹配的关键词，例如：条款101。")
    public List<PolicyClause> searchPolicy(
            @ToolParam(description = "要在制度原文中精确匹配的关键词，例如：条款101") String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (query.strip().length() > MAX_POLICY_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("query 长度不能超过 " + MAX_POLICY_KEYWORD_LENGTH);
        }

        return jdbcTemplate.query(
                POLICY_SEARCH_SQL,
                (resultSet, rowNumber) -> new PolicyClause(
                        emptyIfNull(resultSet.getString("document_title")),
                        emptyIfNull(resultSet.getString("chapter")),
                        emptyIfNull(resultSet.getString("article_number")),
                        emptyIfNull(resultSet.getString("article_title")),
                        emptyIfNull(resultSet.getString("source")),
                        emptyIfNull(resultSet.getString("content")),
                        resultSet.getString("match_type")),
                query.strip(),
                indexingProperties.getCollection());
    }

    @Tool(
            name = "calculate_loan",
            description = "按等额本息方式计算贷款月供、还款总额和总利息。annual_rate 按百分比传入，例如 4.2 表示年利率 4.2%。")
    public LoanCalculation calculateLoan(
            @ToolParam(description = "贷款本金，单位为元，必须大于 0") BigDecimal principal,
            @ToolParam(description = "年利率百分比，例如 4.2 表示 4.2%") BigDecimal annual_rate,
            @ToolParam(description = "贷款期限，单位为年，取值 1 到 50") int years) {
        validateLoanArguments(principal, annual_rate, years);

        int months = Math.multiplyExact(years, 12);
        BigDecimal monthlyPayment = getBigDecimal(principal, annual_rate, months);

        BigDecimal totalRepayment = monthlyPayment.multiply(BigDecimal.valueOf(months), CALCULATION_CONTEXT);
        BigDecimal totalInterest = totalRepayment.subtract(principal, CALCULATION_CONTEXT);

        return new LoanCalculation(
                money(principal),
                annual_rate.stripTrailingZeros(),
                years,
                months,
                money(monthlyPayment),
                money(totalRepayment),
                money(totalInterest));
    }

    private static @NonNull BigDecimal getBigDecimal(BigDecimal principal, BigDecimal annual_rate, int months) {
        BigDecimal monthlyRate = annual_rate.divide(BigDecimal.valueOf(1200), CALCULATION_CONTEXT);
        BigDecimal monthlyPayment;

        if (monthlyRate.signum() == 0) {
            monthlyPayment = principal.divide(BigDecimal.valueOf(months), CALCULATION_CONTEXT);
        }
        else {
            BigDecimal compoundFactor = BigDecimal.ONE
                    .add(monthlyRate, CALCULATION_CONTEXT)
                    .pow(months, CALCULATION_CONTEXT);
            monthlyPayment = principal
                    .multiply(monthlyRate, CALCULATION_CONTEXT)
                    .multiply(compoundFactor, CALCULATION_CONTEXT)
                    .divide(compoundFactor.subtract(BigDecimal.ONE), CALCULATION_CONTEXT);
        }
        return monthlyPayment;
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private void validateLoanArguments(BigDecimal principal, BigDecimal annualRate, int years) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal 必须大于 0");
        }
        if (annualRate == null || annualRate.signum() < 0 || annualRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("annual_rate 必须在 0 到 100 之间");
        }
        if (years < 1 || years > 50) {
            throw new IllegalArgumentException("years 必须在 1 到 50 之间");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record PolicyClause(
            String documentTitle,
            String chapter,
            String articleNumber,
            String articleTitle,
            String source,
            String content,
            String matchType) {
    }

    public record LoanCalculation(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            int years,
            int months,
            BigDecimal monthlyPayment,
            BigDecimal totalRepayment,
            BigDecimal totalInterest) {
    }
}
