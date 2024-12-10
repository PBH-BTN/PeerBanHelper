package io.ipfs.api;

import java.util.*;

import java.lang.reflect.*;

public class JSONParser
{
    private static char skipSpaces(String json, int[] pos)
    {
        while (true)
        {
            if (pos[0] >= json.length())
                return 0;
            char ch = json.charAt(pos[0]);
            if (Character.isWhitespace(ch))
                pos[0]++;
            else
                return ch;
        }
    }

    private static Boolean parseBoolean(String json, int[] pos)
    {
        if (json.regionMatches(pos[0], "true", 0, 4))
        {
            pos[0] += 4;
            return Boolean.TRUE;
        }

        if (json.regionMatches(pos[0], "false", 0, 5))
        {
            pos[0] += 5;
            return Boolean.FALSE;
        }

        return null;
    }

    private static Number parseNumber(String json, int[] pos)
    {
        int endPos = json.length();
        int startPos = pos[0];

        boolean foundExp = false;
        boolean foundDot = false;
        boolean allowPM = true;
        for (int i=startPos; i<endPos; i++)
        {
            char ch = json.charAt(i);
            if ((ch == 'e') || (ch == 'E'))
            {
                if (foundExp)
                    return null;
                allowPM = true;
                foundExp = true;
                continue;
            }

            if ((ch == '+') || (ch == '-'))
            {
                if (allowPM)
                {
                    allowPM = false;
                    ch = skipSpaces(json, pos);
                    if (ch == 0)
                        return null;
                    else
                        continue;
                }
                else
                    return null;
            }

            allowPM = false;
            if (ch == '.')
            {
                if (foundDot)
                    return null;
                foundDot = true;
                continue;
            }

            if (!Character.isDigit(json.charAt(i)))
            {
                pos[0] = endPos = i;
                break;
            }
        }

        if (startPos == endPos)
            return null;

        String numericString = json.substring(startPos, endPos);
        try
        {
            return Integer.parseInt(numericString);
        }
        catch (Exception e) {}

        try
        {
            return Long.parseLong(numericString);
        }
        catch (Exception e) {}

        try
        {
            return Double.parseDouble(numericString);
        }
        catch (Exception e) {}

        throw new IllegalStateException("Failed to parse JSON number at "+startPos+" '"+numericString+"'");
    }

    private static List parseArray(String json, int[] pos)
    {
        int start = pos[0];
        if (json.charAt(start) != '[')
            return null;
        pos[0]++;

        ArrayList result = new ArrayList();
        while (true)
        {
            char ch = skipSpaces(json, pos);
            if (ch == 0)
                break;
            else if (ch == ']')
            {
                pos[0]++;
                return result;
            }
            else if (ch == ',')
            {
                pos[0]++;
                if (skipSpaces(json, pos) == 0)
                    break;
            }

            Object val = parse(json, pos);
            result.add(val);
        }
        throw new IllegalStateException("json Array format at "+start+" ["+(pos[0]-start)+"]  '"+json.substring(start)+"'");
    }

    private static Map parseObject(String json, int[] pos)
    {
        int start = pos[0];
        if (json.charAt(start) != '{')
            return null;
        pos[0]++;

        Map result = new LinkedHashMap();
        while (true)
        {
            char ch = skipSpaces(json, pos);
            if (ch == 0)
                break;
            else if (ch == '}')
            {
                pos[0]++;
                return result;
            }
            else if (ch == ',')
            {
                pos[0]++;
                if (skipSpaces(json, pos) == 0)
                    break;
            }

            String key = parseString(json, pos);
            ch = skipSpaces(json, pos);
            if (ch == 0)
                break;

            pos[0]++;
            if (ch != ':')
                break;

            Object val = parse(json, pos);
            result.put(key, val);
        }
        throw new IllegalStateException("json Object format at "+pos[0]+"  ["+start+", "+json.length()+"]  '"+json.substring(pos[0])+"'");
    }

    private static String parseString(String json, int[] pos)
    {
        int startPos = pos[0];
        if (json.charAt(startPos) != '"')
            return null;
        pos[0]++;

        boolean isEscape = false;
        for (int i=startPos+1; i<json.length(); i++)
        {
            char ch = json.charAt(i);
            if (ch == '\\')
            {
                isEscape = !isEscape;
                continue;
            }

            if (ch == '"')
            {
                if (!isEscape)
                {
                    pos[0] = i+1;
                    return json.substring(startPos+1, i);
                }
            }

            isEscape = false;
        }

        throw new IllegalStateException("json string at at "+startPos+"  '"+json+"'");
    }

    private static Object parse(String json, int[] pos)
    {
        char ch = skipSpaces(json, pos);
        if (ch == 0)
            return null;
        int startPos = pos[0];
        if (startPos == json.length())
            return null;

        Object result = parseArray(json, pos);
        if (result != null)
            return result;

        result = parseObject(json, pos);
        if (result != null)
            return result;

        result = parseBoolean(json, pos);
        if (result != null)
            return result;

        result = parseString(json, pos);
        if (result != null)
            return result;

        result = parseNumber(json, pos);
        if (result != null)
            return result;

        if (json.regionMatches(pos[0], "null", 0, 4))
        {
            pos[0] += 4;
            return null;
        }

        throw new IllegalStateException("json object at at "+startPos+"  '"+json+"'");
    }

    public static Object parse(Object json)
    {
        if (json == null)
            return null;
        return parse(json.toString());
    }

    public static Object parse(String json)
    {
        if (json == null)
            return null;
        return parse(json, new int[1]);
    }

    public static List<Object> parseStream(String json)
    {
        if (json == null)
            return null;
        int[] pos = new int[1];
        List<Object> res = new ArrayList<>();
        json = json.trim();
        while (pos[0] < json.length())
            res.add(parse(json, pos));
        return res;
    }

    private static void escapeString(String s, StringBuffer buf)
    {
        buf.append('"');
        for (int i=0; i<s.length(); i++)
        {
            char ch = s.charAt(i);
            if ((ch == '"') || (ch == '\\'))
                buf.append('\\');
            buf.append(ch);
        }
        buf.append('"');
    }

    private static void toString(Object obj, StringBuffer buf)
    {
        if (obj == null)
            buf.append("null");
        else if ((obj instanceof Boolean) || (obj instanceof Number))
            buf.append(obj.toString());
        else if (obj instanceof Map)
        {
            Map m = (Map) obj;
            boolean first = true;
            Iterator itt = m.keySet().iterator();

            buf.append('{');
            while (itt.hasNext())
            {
                if (!first)
                    buf.append(',');

                String s = (String) itt.next();
                Object val = m.get(s);
                escapeString(s, buf);
                buf.append(":");
                toString(val, buf);
                first = false;
            }
            buf.append('}');
        }
        else if (obj instanceof Object[])
        {
            Object[] l = (Object[]) obj;
            boolean first = true;

            buf.append('[');
            for (int i=0; i<l.length; i++)
            {
                if (!first)
                    buf.append(',');

                toString(l[i], buf);
                first = false;
            }
            buf.append(']');
        }
        else if (obj instanceof List)
        {
            List l = (List) obj;
            boolean first = true;
            Iterator itt = l.iterator();

            buf.append('[');
            while (itt.hasNext())
            {
                if (!first)
                    buf.append(',');

                Object val = itt.next();
                toString(val, buf);
                first = false;
            }
            buf.append(']');
        }
        else if (obj instanceof String)
            escapeString(obj.toString(), buf);
        else
        {
            try
            {
                Class cls = obj.getClass();
                Method m = cls.getDeclaredMethod("toJSON", new Class[0]);
                Object jsonObj = m.invoke(obj, new Object[0]);
                buf.append(toString(jsonObj));
            }
            catch (Exception e)
            {
                escapeString(obj.toString(), buf);
            }
        }
    }

    public static String toString(Object obj)
    {
        StringBuffer buf = new StringBuffer();
        toString(obj, buf);
        return buf.toString();
    }

    public static String stripWhitespace(String src)
    {
        boolean inQuote = false, isEscaped = false;
        StringBuffer buf = new StringBuffer();

        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);

            if (!inQuote)
            {
                if (ch == '"')
                {
                    inQuote = true;
                    isEscaped = false;
                }
                else if (Character.isWhitespace(ch))
                    continue;
            }
            else if (inQuote)
            {
                if (ch == '\\')
                    isEscaped = !isEscaped;
                else if ((ch == '"') && !isEscaped)
                    inQuote = false;
            }

            buf.append(ch);
        }

        return buf.toString();
    }

    public static Object getValue(Object json, String path)
    {
        String[] parts = path.split("\\.");

        for (int i=0; i<parts.length; i++)
        {
            int index = -1;
            String key = parts[i];

            if (key.endsWith("]"))
            {
                int b = key.indexOf("[");
                try
                {
                    index = Integer.parseInt(key.substring(b+1, key.length()-1));
                    key = key.substring(0, b);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("Path syntax error - invalid index");
                }
            }

            if ((json != null) && (json instanceof Map))
                json = ((Map) json).get(key);
            else
                return null;

            if (index >= 0)
            {
                if ((json != null) && (json instanceof List))
                    json = ((List) json).get(index);
                else
                    return null;
            }
        }

        return json;
    }
}
