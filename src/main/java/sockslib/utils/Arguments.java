/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.utils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>Arguments</code> is an argument tool class.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 24, 2015 10:20 AM
 */
public final class Arguments {

    private final String[] args;
    private final Map<String, Integer> argPositionMap;

    public Arguments(final @Nullable String[] args) {
        this.args = args;
        argPositionMap = new HashMap<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argPositionMap.put(args[i], i);
            }
        }
    }

    public static <T> T valueOf(String arg, Class<T> type) {
        checkNotNull(arg, "Argument [arg] may not be null");
        checkNotNull(type, "Argument [type] may not be null");
        Object value = arg.trim();
        if (arg.contains("=")) {
            value = arg.split("=")[1].trim();
        }
        if (type.equals(String.class)) {

        } else if (type.equals(Integer.class)) {
            value = Integer.parseInt((String) value);
        } else if (type.equals(Long.class)) {
            value = Long.parseLong((String) value);
        } else {
            throw new IllegalArgumentException("Not support" + type.getName());
        }
        return (T) value;
    }

    public static String valueOf(String arg) {
        return valueOf(arg, String.class);
    }

    public static int intValueOf(String arg) {
        return valueOf(arg, Integer.class);
    }

    public static long longValueOf(String arg) {
        return valueOf(arg, Long.class);
    }

    public static boolean isHelpArg(String arg) {
        arg = arg.trim();
        return arg.equals("-h") || arg.equals("--help");
    }

    public String argAt(int index) {
        return argAt(index, null);
    }

    public String argAt(int index, @Nullable String defaultValue) {
        if (args == null || index >= args.length || index < 0) {
            return defaultValue;
        } else {
            return args[index];
        }
    }

    public boolean hasArg(String arg) {
        return argPositionMap.get(arg) != null;
    }

    public boolean hasOneOfArgs(String... args) {
        for (String arg : args) {
            if (argPositionMap.get(arg) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean hasArgsIn(String... args) {
        for (String arg : args) {
            if (argPositionMap.get(arg) != null) {
                return true;
            }
        }
        return false;
    }

    public String getValue(String arg, @Nullable String defaultValue) {
        Integer index = argPositionMap.get(checkNotNull(arg));
        if (index == null) {
            return defaultValue;
        } else if (index + 1 < args.length) {
            return args[index + 1];
        }
        return defaultValue;
    }

    public long getLongValue(String arg, long defaultValue) {
        String value = getValue(arg, null);
        if (value == null) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    public int getIntValue(String arg, int defaultValue) {
        String value = getValue(arg, null);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public boolean getBooleanValue(String arg, boolean defaultValue) {
        String value = getValue(arg, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public String getValue(List<String> args, @Nullable String defaultValue) {
        checkNotNull(args);
        String value = defaultValue;
        for (String arg : args) {
            value = getValue(arg, value);
        }
        return value;
    }

    public int getIntValue(List<String> args, int defaultValue) {
        int value = defaultValue;
        for (String arg : args) {
            value = getIntValue(arg, value);
        }
        return value;
    }

    public long getLongValue(List<String> args, long defaultValue) {
        long value = defaultValue;
        for (String arg : args) {
            value = getLongValue(arg, value);
        }
        return value;
    }

    public boolean getBooleanValue(List<String> args, boolean defaultValue) {
        boolean value = defaultValue;
        for (String arg : args) {
            value = getBooleanValue(arg, value);
        }
        return value;
    }

    public String getValueFromArg(String prefix, String splitRegex, @Nullable String defaultValue) {
        checkNotNull(prefix);
        checkNotNull(splitRegex);
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                String[] nameValue = arg.split(splitRegex);
                if (nameValue.length >= 2) {
                    return nameValue[1];
                } else {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    public int getIntValueFromArg(String prefix, String splitRegex, int defaultValue) {
        String value = getValueFromArg(prefix, splitRegex, null);
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public long getLongValueFromArg(String prefix, String splitRegex, long defaultValue) {
        String value = getValueFromArg(prefix, splitRegex, null);
        if (value == null) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    public boolean getBooleanValueFromArg(String prefix, String splitRegex, boolean defaultValue) {
        String value = getValueFromArg(prefix, splitRegex, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public String[] getArgs() {
        return args;
    }

}
