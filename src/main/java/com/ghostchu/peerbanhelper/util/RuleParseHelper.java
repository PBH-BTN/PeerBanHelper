package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import org.slf4j.Logger;

import java.util.Locale;

public class RuleParseHelper {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RuleParseHelper.class);

    public static boolean match(String origin, String ruleString) {
        origin = origin.toLowerCase(Locale.ROOT);
        ruleString = ruleString.toLowerCase(Locale.ROOT);
        String[] ruleExploded = ruleString.split("@", 2);
        if (ruleExploded.length != 2) {
            log.warn(Lang.ERR_INVALID_RULE_SYNTAX, ruleString);
            return false;
        }
        String matchMethod = ruleExploded[0];
        String ruleBody = ruleExploded[1];
        return switch (matchMethod) {
            case "contains" -> origin.contains(ruleBody);
            case "startswith" -> origin.startsWith(ruleBody);
            case "endswith" -> origin.endsWith(ruleBody);
            case "length" -> origin.length() == Integer.parseInt(ruleBody);
            case "equals" -> origin.equals(ruleBody);
            case "regex" -> origin.matches(ruleBody);
            default -> false;
        };
    }
}
