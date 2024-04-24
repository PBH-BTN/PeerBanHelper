package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.util.rule.matcher.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
public class RuleParser {
    public static List<Rule> parse(List<String> string) {
        return string.stream()
                .map(JsonParser::parseString)
                .map(RuleParser::parse)
                .toList();
    }

    public static RuleMatchResult matchRule(List<Rule> rules, String content) {
        RuleMatchResult matchResult = new RuleMatchResult(false, null);
        for (Rule rule : rules) {
            MatchResult result = rule.match(content);
            if (result == MatchResult.NEGATIVE) { // 规则的优先级最高
                return new RuleMatchResult(false, rule);
            }
            if (result == MatchResult.POSITIVE) { // 其次，可被覆盖
                matchResult = new RuleMatchResult(true, rule);
            }
        }
        return matchResult;
    }

    public static Rule parse(JsonElement element) {
        if (element == null) {
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return MatchResult.POSITIVE;
                }
            };
        }
        if (element.isJsonNull()) {
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return MatchResult.NEUTRAL;
                }
            };
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsBoolean() ? MatchResult.POSITIVE : MatchResult.NEGATIVE;
                    }
                };
            }
            if (primitive.isNumber()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsInt() != 0 ? MatchResult.POSITIVE : MatchResult.NEGATIVE;
                    }
                };
            }
            if (primitive.isString()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        String str = primitive.getAsString();
                        return str.equalsIgnoreCase("true") ? MatchResult.POSITIVE : MatchResult.NEGATIVE;
                    }
                };
            }
            throw new IllegalArgumentException("Rule condition (primitive) only accepts boolean or integer");
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Rule condition (jsonobject) only accepts object");
        }
        JsonObject obj = element.getAsJsonObject();
        String method = obj.get("method").getAsString();
        return switch (method) {
            case "STARTS_WITH" -> new StringStartsWithMatcher(obj);
            case "ENDS_WITH" -> new StringEndsWithMatcher(obj);
            case "CONTAINS" -> new StringContainsMatcher(obj);
            case "EQUALS" -> new StringEqualsMatcher(obj);
            case "REGEX" -> new StringRegexMatcher(obj);
            case "LENGTH" -> new StringLengthMatcher(obj);
            default -> throw new IllegalStateException("Unexpected method value: " + method);
        };
    }
}
