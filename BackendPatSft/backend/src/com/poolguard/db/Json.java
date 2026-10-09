package com.poolguard.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, self-contained JSON parser / serializer.
 *
 * The project deliberately avoids third-party dependencies, so this utility
 * provides just enough JSON support for the embedded data store and the
 * REST API payloads.
 *
 * Supported values: Map&lt;String,Object&gt;, List&lt;Object&gt;, String,
 * Long, Double, Boolean and null.
 */
public final class Json {

    private Json() {
    }

    /** Parses a JSON document into Java objects (Map/List/String/Number/Boolean/null). */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing content at position " + parser.pos);
        }
        return value;
    }

    /** Serializes a Java object graph into a JSON document. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Number) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, entry.getKey());
                sb.append(':');
                writeValue(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<Object>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Recursive-descent parser. */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return parseNumber();
            }
        }

        private void expect(String literal) {
            if (!s.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
            }
            pos += literal.length();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // consume '{'
            skipWhitespace();
            if (!atEnd() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (atEnd() || s.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Expected ':' at position " + pos);
                }
                pos++;
                map.put(key, parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated object");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    break;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + (pos - 1));
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // consume '['
            skipWhitespace();
            if (!atEnd() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated array");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    break;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + (pos - 1));
                }
            }
            return list;
        }

        private String parseString() {
            if (atEnd() || s.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected string at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new IllegalArgumentException("Unterminated escape sequence");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw new IllegalArgumentException("Bad unicode escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Bad escape: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = pos;
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E' || (c >= '0' && c <= '9')) {
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (num.isEmpty()) {
                throw new IllegalArgumentException("Expected a value at position " + start);
            }
            if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                return Long.parseLong(num);
            }
            return Double.parseDouble(num);
        }
    }
}
