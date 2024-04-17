package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuleParseHelper {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RuleParseHelper.class);

    public static Map.Entry<Boolean, String> matchMultiple(String origin, List<String> ruleString) {
        boolean result = false;
        String hitRule = null;
        for (String rs : ruleString) {
            AtomicBoolean b = match(origin, rs);
            if (b != null) {
                result = b.get();
                hitRule = rs;
            }
        }
        return Map.entry(result, hitRule);
    }

    @Nullable
    public static AtomicBoolean match(String origin, String ruleString) {
        origin = origin.toLowerCase(Locale.ROOT);
        ruleString = ruleString.toLowerCase(Locale.ROOT);
        boolean reserve = ruleString.startsWith("!");
        if (reserve) {
            ruleString = ruleString.substring(1);
        }
        String[] ruleExploded = ruleString.split("@", 2);
        if (ruleExploded.length != 2) {
            log.warn(Lang.ERR_INVALID_RULE_SYNTAX, ruleString);
            return null;
        }
        String matchMethod = ruleExploded[0];
        String ruleBody = ruleExploded[1];
        boolean r = switch (matchMethod) {
            case "contains" -> origin.contains(ruleBody);
            case "startswith" -> origin.startsWith(ruleBody);
            case "endswith" -> origin.endsWith(ruleBody);
            case "length" -> origin.length() == Integer.parseInt(ruleBody);
            case "equals" -> origin.equals(ruleBody);
            case "regex" -> origin.matches(ruleBody);
            case "isEmpty" -> origin.isEmpty();
            case "isBlank" -> origin.isBlank();
            default -> false;
        };
        if (reserve) {
            return new AtomicBoolean(!r);
        } else {
            if (r) {
                return new AtomicBoolean(r);
            } else {
                return null;
            }
        }
    }
}
