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
     * Parses a list of JSON rule strings into a list of Rule objects.
     *
     * @param string A list of JSON rule strings to be parsed
     * @return A list of parsed Rule objects corresponding to the input JSON strings
     * @throws JsonParseException If any of the input strings cannot be parsed as valid JSON
     */
    public static List<Rule> parse(List<String> string) {
        return string.stream()
                .map(JsonParser::parseString)
                .map(RuleParser::parse)
                .toList();
    }

    /**
     * Matches a given content against a list of rules and determines the overall match result.
     *
     * This method evaluates a list of rules against the provided content, applying a priority-based matching strategy:
     * - If any rule returns a FALSE match, the method immediately returns a failed match result.
     * - If a rule returns a TRUE match, it updates the match result but continues checking other rules.
     * - The final match result reflects the last TRUE match or the initial FALSE state.
     *
     * @param rules   A list of rules to be evaluated against the content
     * @param content The string content to be matched against the rules
     * @return A RuleMatchResult indicating whether the content matches the rules, along with the matching rule and any associated comment
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
     * Parses a JsonElement into a Rule for matching content.
     *
     * This method handles various JSON input types and creates corresponding Rule implementations:
     * - Null or JsonNull elements return a Rule that always matches
     * - Primitive elements (boolean, number, string) create Rules based on their boolean interpretation
     * - JSON objects create specific string matching Rules based on the "method" field
     *
     * @param element The JsonElement to parse into a Rule
     * @return A Rule implementation for matching content
     * @throws IllegalArgumentException If the input is an unsupported JSON type
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
