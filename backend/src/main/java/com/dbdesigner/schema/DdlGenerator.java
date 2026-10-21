package com.dbdesigner.schema;

import com.dbdesigner.schema.Schema.Column;
import com.dbdesigner.schema.Schema.Relation;
import com.dbdesigner.schema.Schema.TableDef;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DdlGenerator {

    public enum Dialect {
        ORACLE(30, Map.ofEntries(
                Map.entry("varchar", "varchar2"), Map.entry("string", "varchar2"), Map.entry("text", "clob"),
                Map.entry("integer", "number(10)"), Map.entry("int", "number(10)"),
                Map.entry("bigint", "number(19)"), Map.entry("smallint", "number(5)"),
                Map.entry("boolean", "number(1)"), Map.entry("datetime", "timestamp"),
                Map.entry("double", "binary_double"), Map.entry("float", "binary_float"))),
        MYSQL(64, Map.ofEntries(
                Map.entry("varchar2", "varchar"), Map.entry("string", "varchar"), Map.entry("number", "decimal"),
                Map.entry("boolean", "tinyint(1)"), Map.entry("clob", "longtext"))),
        POSTGRES(63, Map.ofEntries(
                Map.entry("varchar2", "varchar"), Map.entry("string", "varchar"), Map.entry("number", "numeric"),
                Map.entry("datetime", "timestamp"), Map.entry("double", "double precision"),
                Map.entry("clob", "text")));

        final int maxIdentifier;
        final Map<String, String> typeMap;

        Dialect(int maxIdentifier, Map<String, String> typeMap) {
            this.maxIdentifier = maxIdentifier;
            this.typeMap = typeMap;
        }

        static Dialect of(String dbType) {
            return switch (dbType == null ? "" : dbType.toLowerCase(Locale.ROOT)) {
                case "mysql" -> MYSQL;
                case "postgres", "postgresql" -> POSTGRES;
                default -> ORACLE;
            };
        }

        String quote(String name) {
            return this == MYSQL ? "`" + name + "`" : name;
        }

        String mapType(String raw) {
            if (raw == null || raw.isBlank()) {
                return this == ORACLE ? "varchar2(255)" : "varchar(255)";
            }
            String type = raw.trim().toLowerCase(Locale.ROOT);
            int paren = type.indexOf('(');
            String base = paren < 0 ? type : type.substring(0, paren).trim();
            String args = paren < 0 ? "" : type.substring(paren);
            String mapped = typeMap.get(base);
            if (mapped == null) {
                return type;
            }
            return mapped.contains("(") ? mapped : mapped + args;
        }
    }

    public String generate(Schema schema, String dbType) {
        Dialect dialect = Dialect.of(dbType);
        Map<String, TableDef> tables = new HashMap<>();
        schema.tables().forEach(t -> tables.put(t.id(), t));

        StringBuilder sql = new StringBuilder();
        for (TableDef table : schema.tables()) {
            sql.append("CREATE TABLE ").append(dialect.quote(table.name())).append(" (\n");
            List<String> lines = new java.util.ArrayList<>();
            for (Column c : table.columns()) {
                StringBuilder line = new StringBuilder("  ")
                        .append(dialect.quote(c.name())).append(' ').append(dialect.mapType(c.type()));
                if (c.defaultValue() != null && !c.defaultValue().isBlank()) {
                    line.append(" DEFAULT ").append(c.defaultValue().trim());
                }
                if (!c.isNullable() || c.pk()) {
                    line.append(" NOT NULL");
                }
                if (c.unique() && !c.pk()) {
                    line.append(" UNIQUE");
                }
                lines.add(line.toString());
            }
            String pks = table.columns().stream().filter(Column::pk)
                    .map(c -> dialect.quote(c.name())).collect(Collectors.joining(", "));
            if (!pks.isEmpty()) {
                lines.add("  PRIMARY KEY (" + pks + ")");
            }
            sql.append(String.join(",\n", lines)).append("\n);\n\n");
        }

        Set<String> usedNames = new HashSet<>();
        for (Relation rel : schema.relations()) {
            TableDef src = tables.get(rel.sourceTable());
            TableDef dst = tables.get(rel.targetTable());
            if (src == null || dst == null) {
                continue;
            }
            String srcCol = columnName(src, rel.sourceColumn());
            String dstCol = columnName(dst, rel.targetColumn());
            if (srcCol == null || dstCol == null) {
                continue;
            }
            String constraint = uniqueName("FK_" + src.name() + "_" + srcCol, dialect.maxIdentifier, usedNames);
            sql.append("ALTER TABLE ").append(dialect.quote(src.name()))
                    .append(" ADD CONSTRAINT ").append(dialect.quote(constraint))
                    .append(" FOREIGN KEY (").append(dialect.quote(srcCol)).append(")")
                    .append(" REFERENCES ").append(dialect.quote(dst.name()))
                    .append(" (").append(dialect.quote(dstCol)).append(");\n");
        }
        return sql.toString().stripTrailing() + "\n";
    }

    private static String columnName(TableDef table, String columnId) {
        return table.columns().stream().filter(c -> c.id().equals(columnId))
                .map(Column::name).findFirst().orElse(null);
    }

    private static String uniqueName(String base, int max, Set<String> used) {
        String name = base.length() > max ? base.substring(0, max) : base;
        int n = 2;
        while (!used.add(name.toUpperCase(Locale.ROOT))) {
            String suffix = "_" + n++;
            name = (base.length() + suffix.length() > max ? base.substring(0, max - suffix.length()) : base) + suffix;
        }
        return name;
    }
}
