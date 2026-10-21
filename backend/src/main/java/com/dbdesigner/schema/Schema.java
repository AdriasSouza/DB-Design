package com.dbdesigner.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Modelo do diagrama, no mesmo formato JSON usado pelo frontend. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Schema(List<TableDef> tables, List<Relation> relations) {

    public Schema {
        tables = tables == null ? List.of() : tables;
        relations = relations == null ? List.of() : relations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableDef(String id, String name, String color, double x, double y, List<Column> columns) {
        public TableDef {
            columns = columns == null ? List.of() : columns;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Column(String id, String name, String type, boolean pk, Boolean nullable, boolean unique,
                         String defaultValue) {
        public boolean isNullable() {
            return nullable == null || nullable;
        }
    }

    /** Origem = coluna que referencia (FK); destino = coluna referenciada (PK). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Relation(String id, String sourceTable, String sourceColumn, String sourceHandle,
                           String targetTable, String targetColumn, String targetHandle) {
    }
}
