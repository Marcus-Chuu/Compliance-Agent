package com.nbcb.assistagent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PromptInjectionGuard {

    private static final List<DetectionRule> RULES = List.of(
            rule("IGNORE_PREVIOUS_INSTRUCTIONS",
                    "(?:ignore|disregard|forget|override|bypass|discard)\\s+(?:all\\s+)?"
                            + "(?:previous|prior|above|system|developer)\\s+"
                            + "(?:instructions?|prompts?|rules?|messages?)"),
            rule("CHINESE_IGNORE_INSTRUCTIONS",
                    "(?:忽略|无视|忘记|覆盖|绕过|不再遵守).{0,16}"
                            + "(?:之前|先前|以上|上述|系统|开发者).{0,12}"
                            + "(?:指令|提示词|规则|要求|消息)"),
            rule("REVEAL_HIDDEN_PROMPT",
                    "(?:reveal|show|print|output|repeat|leak|expose).{0,24}"
                            + "(?:system|developer|hidden).{0,12}"
                            + "(?:prompt|instructions?|message)"),
            rule("CHINESE_REVEAL_PROMPT",
                    "(?:输出|显示|打印|复述|重复|泄露|告诉我).{0,20}"
                            + "(?:系统|开发者|隐藏|内部).{0,12}"
                            + "(?:提示词|指令|消息|规则)"),
            rule("ROLE_INJECTION_MARKUP",
                    "(?:<\\|(?:system|assistant|developer)\\|>"
                            + "|\\[(?:system|assistant|developer)]"
                            + "|(?:^|\\n)\\s*(?:system|developer)\\s*:)"),
            rule("JAILBREAK_MODE",
                    "(?:jailbreak|developer\\s+mode|do\\s+anything\\s+now|dan\\s+mode"
                            + "|越狱模式|开发者模式|无限制模式)"));

    private static final List<String> COMPACT_SIGNATURES = List.of(
            "ignorepreviousinstructions",
            "ignoreallpreviousinstructions",
            "disregardpreviousinstructions",
            "forgetpreviousinstructions",
            "revealsystemprompt",
            "showsystemprompt",
            "printsystemprompt",
            "忽略之前的指令",
            "忽略以上指令",
            "无视之前的提示词",
            "输出系统提示词",
            "显示系统提示词",
            "泄露内部指令");

    public void validate(String userInput) {
        if (userInput == null) {
            return;
        }

        NormalizedInput normalized = normalize(userInput);
        for (DetectionRule rule : RULES) {
            if (rule.pattern().matcher(normalized.text()).find()) {
                reject(rule.id(), userInput.length());
            }
        }

        String compact = normalized.text().replaceAll("[^\\p{L}\\p{N}]", "");
        for (String signature : COMPACT_SIGNATURES) {
            if (compact.contains(signature)) {
                reject("OBFUSCATED_INJECTION", userInput.length());
            }
        }

        if (normalized.removedControlCharacters() > 4) {
            reject("EXCESSIVE_CONTROL_CHARACTERS", userInput.length());
        }
    }

    private NormalizedInput normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        StringBuilder cleaned = new StringBuilder(normalized.length());
        int removedControlCharacters = 0;

        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);

            if (type == Character.FORMAT) {
                removedControlCharacters++;
                continue;
            }
            if (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                removedControlCharacters++;
                continue;
            }
            cleaned.appendCodePoint(codePoint);
        }

        String text = cleaned.toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .strip();
        return new NormalizedInput(text, removedControlCharacters);
    }

    private void reject(String ruleId, int inputLength) {
        log.warn("Blocked suspected prompt injection: rule={}, inputLength={}", ruleId, inputLength);
        throw new PromptInjectionException();
    }

    private static DetectionRule rule(String id, String regex) {
        return new DetectionRule(id, Pattern.compile(
                regex,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL));
    }

    private record DetectionRule(String id, Pattern pattern) {
    }

    private record NormalizedInput(String text, int removedControlCharacters) {
    }
}
