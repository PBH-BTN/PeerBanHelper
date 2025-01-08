package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.matcher.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
public final class RuleParser {
    /**
     * Parses a list of JSON strings into a list of Rule objects.
     *
     * @param string A list of JSON-formatted strings to be parsed into rules
     * @return A list of Rule objects created from the input JSON strings
     * @throws IllegalArgumentException If any of the JSON strings cannot be parsed
     */
    public static List<Rule> parse(List<String> string) {
        return string.stream()
                .map(JsonParser::parseString)
                .map(RuleParser::parse)
                .toList();
    }

    /**
     * Matches a list of rules against a given content string and determines the best matching rule.
     *
     * This method iterates through a collection of rules and evaluates each rule against the provided content.
     * It follows a priority-based matching strategy:
     * 1. If any rule returns a definitive FALSE match, the method immediately returns a failed match result.
     * 2. If a rule returns a TRUE match, it updates the match result but continues checking other rules.
     * 3. If no rules match or override the result, the last match result is returned.
     *
     * @param rules    A list of {@code Rule} objects to be evaluated
     * @param content  The content string to match against the rules
     * @return         A {@code RuleMatchResult} indicating the outcome of rule matching
     *                 - Contains a boolean indicating match success
     *                 - The matching rule (if any)
     *                 - An optional comment describing the match result
     */
    public static RuleMatchResult matchRule(List<Rule> rules, String content) {
        RuleMatchResult matchResult = new RuleMatchResult(false, null, null);
        for (Rule rule : rules) {
            MatchResult result = rule.match(content);
            if (result.result() == MatchResultEnum.FALSE) { // 规则的优先级最高
                return new RuleMatchResult(false, rule, result.comment());
            }
            if (result.result() == MatchResultEnum.TRUE) { // 其次，可被覆盖
                matchResult = new RuleMatchResult(true, rule,result.comment());
            }

        }

        return matchResult;
    }

    /**
     * Parses a JSON element into a Rule object for matching content.
     *
     * This method handles various JSON element types and creates corresponding Rule implementations:
     * - Null or JsonNull elements return a Rule that always matches
     * - Primitive elements (boolean, number, string) create Rules based on their boolean interpretation
     * - JSON objects create specific string matching Rules based on the "method" field
     *
     * @param element The JSON element to parse into a Rule
     * @return A Rule object capable of matching content
     * @throws IllegalArgumentException If the JSON element is an unsupported type
     * @throws IllegalStateException If an unrecognized matching method is specified
     */
    public static Rule parse(JsonElement element) {
        if (element == null) {
            // 虚拟规则不记录数据
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return new MatchResult(MatchResultEnum.TRUE, null);
                }

                @Override
                public String metadata() {
                    return "";
                }

                @Override
                public String matcherIdentifier() {
                    return "dumb:null";
                }
            };
        }
        if (element.isJsonNull()) {
            return new Rule() {
                @Override
                public @NotNull MatchResult match(@NotNull String content) {
                    return new MatchResult(MatchResultEnum.TRUE, null);
                }

                @Override
                public String metadata() {
                    return "";
                }

                @Override
                public String matcherIdentifier() {
                    return "dumb:jsonnull";
                }
            };
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsBoolean() ? new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN)) : new MatchResult(MatchResultEnum.FALSE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN));
                    }

                    @Override
                    public String metadata() {
                        return "";
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:boolean";
                    }
                };
            }
            if (primitive.isNumber()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        return primitive.getAsBoolean() ? new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN_BY_INTEGER)) : new MatchResult(MatchResultEnum.FALSE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN_BY_INTEGER));
                    }

                    @Override
                    public String metadata() {
                        return "";
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:number";
                    }
                };
            }
            if (primitive.isString()) {
                return new Rule() {
                    @Override
                    public @NotNull MatchResult match(@NotNull String content) {
                        String str = primitive.getAsString();
                        return Boolean.parseBoolean(str) ? new MatchResult(MatchResultEnum.TRUE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN_BY_STRING)) : new MatchResult(MatchResultEnum.FALSE, new TranslationComponent(Lang.MATCH_CONDITION_BOOLEAN_BY_STRING));
                    }

                    @Override
                    public String metadata() {
                        return "";
                    }

                    @Override
                    public String matcherIdentifier() {
                        return "dumb:boolstring";
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
