package com.dbdesigner.schema;

import com.dbdesigner.schema.Schema.Column;
import com.dbdesigner.schema.Schema.Relation;
import com.dbdesigner.schema.Schema.TableDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lê scripts DDL (CREATE TABLE / ALTER TABLE ... FOREIGN KEY) de Oracle, MySQL e PostgreSQL. */
@Component
public class DdlImporter {

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern CREATE = Pattern.compile(
            "CREATE\\s+(?:GLOBAL\\s+TEMPORARY\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([^\\s(]+)\\s*\\(", CI);
    private static final Pattern ALTER_FK = Pattern.compile(
            "ALTER\\s+TABLE\\s+(?:ONLY\\s+)?(\\S+)\\s+ADD\\s+(?:CONSTRAINT\\s+\\S+\\s+)?FOREIGN\\s+KEY\\s*\\(([^)]*)\\)"
                    + "\\s*REFERENCES\\s+([^\\s(]+)\\s*\\(([^)]*)\\)", CI);
    private static final Pattern TABLE_FK = Pattern.compile(
            "^(?:CONSTRAINT\\s+\\S+\\s+)?FOREIGN\\s+KEY\\s*\\(([^)]*)\\)\\s*REFERENCES\\s+([^\\s(]+)\\s*\\(([^)]*)\\)", CI);
    private static final Pattern TABLE_PK = Pattern.compile(
            "^(?:CONSTRAINT\\s+\\S+\\s+)?PRIMARY\\s+KEY\\s*\\(([^)]*)\\)", CI);
    private static final Pattern SKIPPED = Pattern.compile(
            "^(?:CONSTRAINT|UNIQUE|KEY|INDEX|CHECK|FULLTEXT|SPATIAL)\\b", CI);
    private static final Pattern COLUMN = Pattern.compile(
            "^\\s*(\"[^\"]+\"|`[^`]+`|\\[[^\\]]+]|\\S+)\\s+(.*)$", Pattern.DOTALL);
    private static final Pattern TYPE_END = Pattern.compile(
            "^(.*?)(?=\\s+(?:NOT\\s+NULL|NULL|DEFAULT|PRIMARY|UNIQUE|REFERENCES|AUTO_INCREMENT|GENERATED|CONSTRAINT"
                    + "|COMMENT|CHECK|IDENTITY|ENABLE|COLLATE)\\b|$)", CI | Pattern.DOTALL);
    private static final Pattern INLINE_REF = Pattern.compile("REFERENCES\\s+([^\\s(]+)\\s*\\(([^)]*)\\)", CI);
    private static final Pattern DEFAULT = Pattern.compile("DEFAULT\\s+('[^']*'|\\S+)", CI);

    private static final class WorkColumn {
        String name;
        String type;
        boolean pk;
        boolean nullable = true;
        boolean unique;
        String defaultValue;
    }

    private static final class WorkTable {
        final String name;
        final Map<String, WorkColumn> columns = new LinkedHashMap<>();

        WorkTable(String name) {
            this.name = name;
        }
    }

    private record PendingFk(String table, String column, String refTable, String refColumn) {
    }

    public Schema parse(String sql) {
        String script = sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\r\\n]*", " ");
        Map<String, WorkTable> tables = new LinkedHashMap<>();
        List<PendingFk> fks = new ArrayList<>();

        Matcher create = CREATE.matcher(script);
        while (create.find()) {
            int end = closingParen(script, create.end());
            if (end < 0) {
                break;
            }
            WorkTable table = new WorkTable(clean(create.group(1)));
            for (String part : splitTopLevel(script.substring(create.end(), end))) {
                parsePart(table, part.trim(), fks);
            }
            tables.put(key(table.name), table);
        }

        Matcher alter = ALTER_FK.matcher(script);
        while (alter.find()) {
            addFks(fks, clean(alter.group(1)), alter.group(2), clean(alter.group(3)), alter.group(4));
        }
        return build(tables, fks);
    }

    private void parsePart(WorkTable table, String part, List<PendingFk> fks) {
        if (part.isEmpty()) {
            return;
        }
        Matcher pk = TABLE_PK.matcher(part);
        if (pk.find()) {
            for (String col : split(pk.group(1))) {
                WorkColumn c = table.columns.get(key(col));
                if (c != null) {
                    c.pk = true;
                    c.nullable = false;
                }
            }
            return;
        }
        Matcher fk = TABLE_FK.matcher(part);
        if (fk.find()) {
            addFks(fks, table.name, fk.group(1), clean(fk.group(2)), fk.group(3));
            return;
        }
        if (SKIPPED.matcher(part).find()) {
            return;
        }
        Matcher col = COLUMN.matcher(part);
        if (!col.matches()) {
            return;
        }
        WorkColumn c = new WorkColumn();
        c.name = clean(col.group(1));
        String rest = col.group(2);
        Matcher type = TYPE_END.matcher(rest);
        c.type = (type.find() ? type.group(1) : rest).trim().toLowerCase(Locale.ROOT);
        c.pk = Pattern.compile("PRIMARY\\s+KEY", CI).matcher(rest).find();
        c.nullable = !c.pk && !Pattern.compile("NOT\\s+NULL", CI).matcher(rest).find();
        c.unique = Pattern.compile("\\bUNIQUE\\b", CI).matcher(rest).find();
        Matcher def = DEFAULT.matcher(rest);
        if (def.find()) {
            c.defaultValue = def.group(1);
        }
        table.columns.put(key(c.name), c);
        Matcher ref = INLINE_REF.matcher(rest);
        if (ref.find()) {
            fks.add(new PendingFk(table.name, c.name, clean(ref.group(1)), clean(split(ref.group(2)).get(0))));
        }
    }

    private void addFks(List<PendingFk> fks, String table, String cols, String refTable, String refCols) {
        List<String> src = split(cols);
        List<String> dst = split(refCols);
        for (int i = 0; i < Math.min(src.size(), dst.size()); i++) {
            fks.add(new PendingFk(table, src.get(i), refTable, dst.get(i)));
        }
    }

    private Schema build(Map<String, WorkTable> tables, List<PendingFk> fks) {
        Map<String, String> tableIds = new LinkedHashMap<>();
        Map<String, String> columnIds = new LinkedHashMap<>();
        List<TableDef> defs = new ArrayList<>();
        int index = 0;
        for (WorkTable t : tables.values()) {
            String tableId = newId("t");
            tableIds.put(key(t.name), tableId);
            List<Column> cols = new ArrayList<>();
            for (WorkColumn c : t.columns.values()) {
                String colId = newId("c");
                columnIds.put(key(t.name) + "." + key(c.name), colId);
                cols.add(new Column(colId, c.name, c.type, c.pk, c.nullable, c.unique, c.defaultValue));
            }
            defs.add(new TableDef(tableId, t.name, "#3182ce", (index % 4) * 320, (index / 4) * 340, cols));
            index++;
        }
        List<Relation> relations = new ArrayList<>();
        for (PendingFk fk : fks) {
            String srcTable = tableIds.get(key(fk.table));
            String dstTable = tableIds.get(key(fk.refTable));
            String srcCol = columnIds.get(key(fk.table) + "." + key(fk.column));
            String dstCol = columnIds.get(key(fk.refTable) + "." + key(fk.refColumn));
            if (srcTable != null && dstTable != null && srcCol != null && dstCol != null) {
                relations.add(new Relation(newId("r"), srcTable, srcCol, null, dstTable, dstCol, null));
            }
        }
        return new Schema(defs, relations);
    }

    private static int closingParen(String s, int from) {
        int depth = 1;
        boolean quoted = false;
        for (int i = from; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
            } else if (!quoted && ch == '(') {
                depth++;
            } else if (!quoted && ch == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'') {
                quoted = !quoted;
            } else if (!quoted && ch == '(') {
                depth++;
            } else if (!quoted && ch == ')') {
                depth--;
            } else if (!quoted && depth == 0 && ch == ',') {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static List<String> split(String csv) {
        Set<String> names = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            if (!s.isBlank()) {
                names.add(clean(s));
            }
        }
        return new ArrayList<>(names);
    }

    /** Remove aspas/colchetes e o prefixo de schema (SCHEMA.TABELA -> TABELA). */
    private static String clean(String identifier) {
        String id = identifier.trim();
        int dot = id.lastIndexOf('.');
        if (dot >= 0) {
            id = id.substring(dot + 1);
        }
        return id.replaceAll("^[\"`\\[]|[\"`\\]]$", "").trim();
    }

    private static String key(String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    private static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
