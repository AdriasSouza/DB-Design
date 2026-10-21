package com.dbdesigner.schema;

import com.dbdesigner.schema.Schema.Column;
import com.dbdesigner.schema.Schema.Relation;
import com.dbdesigner.schema.Schema.TableDef;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Lê tabelas, colunas e chaves de um schema Oracle consultando o catálogo (ALL_*). */
@Component
public class OracleReverseEngineer {

    public record Connect(String host, int port, String service, boolean sid, String user, String password,
                          String schema) {
    }

    private static final String COLUMNS_SQL = """
            SELECT c.table_name, c.column_name, c.data_type, c.char_length, c.data_precision, c.data_scale,
                   c.nullable, c.data_default
              FROM all_tab_columns c
              JOIN all_tables t ON t.owner = c.owner AND t.table_name = c.table_name
             WHERE c.owner = ? AND c.table_name NOT LIKE 'BIN$%'
             ORDER BY c.table_name, c.column_id""";

    // data_default é LONG: precisa ser a última coluna lida do select.

    private static final String KEYS_SQL = """
            SELECT c.constraint_type, c.table_name, cc.column_name, cc.position,
                   (SELECT COUNT(*) FROM all_cons_columns x
                     WHERE x.owner = c.owner AND x.constraint_name = c.constraint_name) AS col_count
              FROM all_constraints c
              JOIN all_cons_columns cc ON cc.owner = c.owner AND cc.constraint_name = c.constraint_name
             WHERE c.owner = ? AND c.constraint_type IN ('P', 'U')""";

    private static final String FKS_SQL = """
            SELECT c.table_name, cc.column_name, rc.table_name AS ref_table, rcc.column_name AS ref_column
              FROM all_constraints c
              JOIN all_cons_columns cc ON cc.owner = c.owner AND cc.constraint_name = c.constraint_name
              JOIN all_constraints rc ON rc.owner = c.r_owner AND rc.constraint_name = c.r_constraint_name
              JOIN all_cons_columns rcc ON rcc.owner = rc.owner AND rcc.constraint_name = rc.constraint_name
                                       AND rcc.position = cc.position
             WHERE c.owner = ? AND c.constraint_type = 'R' AND rc.owner = c.owner""";

    public Schema read(Connect req) throws SQLException {
        String url = req.sid()
                ? "jdbc:oracle:thin:@" + req.host() + ":" + req.port() + ":" + req.service()
                : "jdbc:oracle:thin:@//" + req.host() + ":" + req.port() + "/" + req.service();
        Properties props = new Properties();
        props.setProperty("user", req.user());
        props.setProperty("password", req.password());
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        props.setProperty("oracle.jdbc.ReadTimeout", "60000");

        String owner = (req.schema() == null || req.schema().isBlank() ? req.user() : req.schema())
                .trim().toUpperCase(Locale.ROOT);

        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setReadOnly(true);
            return build(conn, owner);
        }
    }

    private Schema build(Connection conn, String owner) throws SQLException {
        Map<String, List<Column>> columns = new LinkedHashMap<>();
        Map<String, Set<String>> pks = new HashMap<>();
        Map<String, Set<String>> uniques = new HashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(COLUMNS_SQL)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString(1);
                    String name = rs.getString(2);
                    String type = formatType(rs.getString(3), rs.getInt(4), nullableInt(rs, 5), nullableInt(rs, 6));
                    boolean nullable = "Y".equals(rs.getString(7));
                    String def = rs.getString(8);
                    columns.computeIfAbsent(table, k -> new ArrayList<>())
                            .add(new Column(newId("c"), name, type, false, nullable, false,
                                    def == null || def.isBlank() ? null : def.trim()));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Nenhuma tabela encontrada no schema " + owner
                    + " (verifique o nome do schema e as permissões de leitura).");
        }

        try (PreparedStatement ps = conn.prepareStatement(KEYS_SQL)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean primary = "P".equals(rs.getString(1));
                    // Unique só é marcado em restrições de uma única coluna.
                    if (primary || rs.getInt(5) == 1) {
                        (primary ? pks : uniques).computeIfAbsent(rs.getString(2), k -> new HashSet<>())
                                .add(rs.getString(3));
                    }
                }
            }
        }

        List<TableDef> tables = new ArrayList<>();
        Map<String, TableDef> byName = new HashMap<>();
        int index = 0;
        for (Map.Entry<String, List<Column>> entry : columns.entrySet()) {
            String table = entry.getKey();
            List<Column> cols = new ArrayList<>();
            for (Column c : entry.getValue()) {
                boolean pk = pks.getOrDefault(table, Set.of()).contains(c.name());
                boolean unique = uniques.getOrDefault(table, Set.of()).contains(c.name());
                cols.add(new Column(c.id(), c.name(), c.type(), pk, c.isNullable() && !pk, unique, c.defaultValue()));
            }
            TableDef def = new TableDef(newId("t"), table, "#3182ce", (index % 4) * 320, (index / 4) * 340, cols);
            tables.add(def);
            byName.put(table, def);
            index++;
        }

        List<Relation> relations = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(FKS_SQL)) {
            ps.setString(1, owner);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableDef src = byName.get(rs.getString(1));
                    TableDef dst = byName.get(rs.getString(3));
                    String srcCol = columnId(src, rs.getString(2));
                    String dstCol = columnId(dst, rs.getString(4));
                    if (srcCol != null && dstCol != null) {
                        relations.add(new Relation(newId("r"), src.id(), srcCol, null, dst.id(), dstCol, null));
                    }
                }
            }
        }
        return new Schema(tables, relations);
    }

    /** Converte o tipo do catálogo para a forma usada no diagrama, ex.: VARCHAR2 + 50 -> varchar2(50). */
    static String formatType(String dataType, int charLength, Integer precision, Integer scale) {
        String type = dataType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "varchar2", "nvarchar2", "char", "nchar" -> charLength > 0 ? type + "(" + charLength + ")" : type;
            case "number" -> precision == null ? "number"
                    : scale == null || scale == 0 ? "number(" + precision + ")"
                    : "number(" + precision + "," + scale + ")";
            default -> type;
        };
    }

    private static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static String columnId(TableDef table, String column) {
        if (table == null) {
            return null;
        }
        return table.columns().stream().filter(c -> c.name().equals(column)).map(Column::id).findFirst().orElse(null);
    }

    private static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
