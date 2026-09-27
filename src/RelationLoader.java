import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads relation definitions (Section 4.1) into a Map<String, Relation>.
 *
 * DESIGN NOTE (see also GRAMMAR.md): this is deliberately NOT built on top
 * of Tokenizer/Parser. The query language is whitespace-insensitive --
 * cases 1 and 2 require "select[x1=3](R)" and "select[ x1 = 3 ](R)" to
 * tokenize identically. But a relation body uses a bare NEWLINE to
 * separate one tuple from the next:
 *
 *   Employees (EID, Name, Age, DID) = {
 *     E1, John, 32, D1
 *     E2, Alice, 28, D2
 *   }
 *
 * there is no comma or other token between the two tuple lines. Making
 * the one Tokenizer newline-sensitive only inside "{ }" blocks would need
 * lexer modes/state that the rest of the language never needs. A small
 * dedicated line-oriented reader for just this one part of the grammar is
 * simpler to write and to explain than a mode-switching lexer, at the
 * cost of not sharing code with the query tokenizer. Line numbers are
 * used for its error messages instead of character positions.
 */
public final class RelationLoader {

    private record HeaderParts(String name, List<String> attrNames) {}

    public static Map<String, Relation> load(String source) {
        Map<String, Relation> relations = new LinkedHashMap<>();
        String[] rawLines = source.split("\n", -1);

        int i = 0;
        while (i < rawLines.length) {
            String line = rawLines[i].trim();
            int lineNumber = i + 1;
            i++;
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }

            HeaderParts header = parseHeader(line, lineNumber);
            if (relations.containsKey(header.name())) {
                throw new SchemaException("relation '" + header.name() + "' is defined more than once");
            }

            List<Attribute> columns = new ArrayList<>();
            for (String attrName : header.attrNames()) {
                columns.add(new Attribute(header.name(), attrName));
            }
            Schema schema = new Schema(columns);

            Set<Tuple> tuples = new LinkedHashSet<>();
            boolean closed = false;
            while (i < rawLines.length) {
                String bodyLine = rawLines[i].trim();
                int bodyLineNumber = i + 1;
                i++;
                if (bodyLine.isEmpty() || bodyLine.startsWith("//")) {
                    continue;
                }
                if (bodyLine.equals("}")) {
                    closed = true;
                    break;
                }
                List<String> rawValues = splitValues(bodyLine);
                if (rawValues.size() != columns.size()) {
                    throw new SchemaException("line " + bodyLineNumber + ": relation '" + header.name()
                            + "' declares " + columns.size() + " attributes but this tuple has "
                            + rawValues.size() + " values: " + bodyLine);
                }
                List<Value> values = new ArrayList<>();
                for (String raw : rawValues) {
                    values.add(parseValue(raw));
                }
                tuples.add(new Tuple(values));
            }
            if (!closed) {
                throw new SchemaException("relation '" + header.name() + "' is missing a closing '}'");
            }

            relations.put(header.name(), new Relation(schema, tuples));
        }
        return relations;
    }

    private static HeaderParts parseHeader(String line, int lineNumber) {
        int open = line.indexOf('(');
        int close = line.indexOf(')');
        if (open < 0 || close < 0 || close < open) {
            throw new SchemaException("line " + lineNumber
                    + ": expected 'Name (Attr, ...) = {' but found: " + line);
        }
        String name = line.substring(0, open).trim();
        if (name.isEmpty()) {
            throw new SchemaException("line " + lineNumber + ": relation definition is missing a name: " + line);
        }
        List<String> attrNames = new ArrayList<>();
        for (String part : line.substring(open + 1, close).split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new SchemaException("line " + lineNumber + ": empty attribute name in: " + line);
            }
            attrNames.add(trimmed);
        }
        String rest = line.substring(close + 1).trim();
        if (!rest.equals("= {")) {
            throw new SchemaException("line " + lineNumber
                    + ": expected '= {' after the attribute list, found: '" + rest + "'");
        }
        return new HeaderParts(name, attrNames);
    }

    /**
     * Splits one tuple line on commas, respecting single-quoted strings --
     * the same quoting rules as Tokenizer.scanString (a doubled quote '' is
     * one literal quote character; commas/parens/spaces inside quotes don't
     * split or terminate anything).
     */
    private static List<String> splitValues(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        int len = line.length();
        while (i < len) {
            char c = line.charAt(i);
            if (c == '\'') {
                current.append(c);
                i++;
                while (true) {
                    if (i >= len) {
                        throw new SchemaException("unterminated string literal in tuple: " + line);
                    }
                    char sc = line.charAt(i);
                    current.append(sc);
                    i++;
                    if (sc == '\'') {
                        if (i < len && line.charAt(i) == '\'') {
                            current.append('\'');
                            i++;
                            continue;
                        }
                        break;
                    }
                }
            } else if (c == ',') {
                parts.add(current.toString().trim());
                current.setLength(0);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        parts.add(current.toString().trim());
        return parts;
    }

    private static Value parseValue(String raw) {
        if (raw.startsWith("'")) {
            if (!raw.endsWith("'") || raw.length() < 2) {
                throw new SchemaException("malformed string literal: " + raw);
            }
            String decoded = raw.substring(1, raw.length() - 1).replace("''", "'");
            return new Value.StrValue(decoded);
        }
        try {
            return new Value.NumValue(new BigDecimal(raw));
        } catch (NumberFormatException e) {
            return new Value.StrValue(raw); // bare (unquoted) string
        }
    }
}
