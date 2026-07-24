package com.nbcb.assistagent.app;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class QueryIntentRouter {

    private static final Pattern LOAN_CALCULATION_PATTERN = Pattern.compile(
            "(?:计算|测算|月供|总利息|还款额|等额本息|等额本金|贷款计算器|"
                    + "calculate\\s+(?:a\\s+)?loan|monthly\\s+payment|total\\s+interest|amortization)"
                    + "|(?:\\d|万|元).{0,40}年利率.{0,40}(?:期限|\\d+\\s*[年月])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final Pattern POLICY_PATTERN = Pattern.compile(
            "(?:制度|条款|第[一二三四五六七八九十百千万零〇0-9]+条|合规|信贷|贷款|授信|审批|准入|"
                    + "贷前|贷中|贷后|风险分类|展期|借新还旧|贷款用途|反洗钱|内控|授权|"
                    + "放款|担保|征信|管理办法|操作规程|业务规则|监管要求|制度依据|政策规定)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public QueryIntent route(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return QueryIntent.GENERAL;
        }

        String normalized = Normalizer.normalize(userMessage, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip();
        if (LOAN_CALCULATION_PATTERN.matcher(normalized).find()) {
            return QueryIntent.LOAN_CALCULATION;
        }
        if (POLICY_PATTERN.matcher(normalized).find()) {
            return QueryIntent.POLICY;
        }
        return QueryIntent.GENERAL;
    }

    public boolean shouldUseRag(String userMessage) {
        return route(userMessage) == QueryIntent.POLICY;
    }

    public enum QueryIntent {
        POLICY,
        LOAN_CALCULATION,
        GENERAL
    }
}
