package com.dbdesigner.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdlTest {

    private static final String ORACLE_DDL = """
            -- devedores
            CREATE TABLE DIFAL_DEVEDORES (
              ID NUMBER(10) NOT NULL,
              LOTE VARCHAR2(20),
              CNPJ_CPF_EMITENTE VARCHAR2(14) NOT NULL,
              TOTAL_ABERTO NUMBER(12,2) DEFAULT 0,
              PRIMARY KEY (ID)
            );
            CREATE TABLE SOCITA_IE (
              CNPJ_CPF_EMITENTE VARCHAR2(14) PRIMARY KEY,
              STATUS NUMBER(1)
            );
            ALTER TABLE DIFAL_DEVEDORES ADD CONSTRAINT FK_DEV FOREIGN KEY (CNPJ_CPF_EMITENTE)
              REFERENCES SOCITA_IE (CNPJ_CPF_EMITENTE);
            """;

    private final DdlImporter importer = new DdlImporter();
    private final DdlGenerator generator = new DdlGenerator();

    @Test
    void importsTablesColumnsAndForeignKeys() {
        Schema schema = importer.parse(ORACLE_DDL);

        assertEquals(2, schema.tables().size());
        Schema.TableDef devedores = schema.tables().get(0);
        assertEquals("DIFAL_DEVEDORES", devedores.name());
        assertEquals(4, devedores.columns().size());
        assertTrue(devedores.columns().get(0).pk());
        assertEquals("number(12,2)", devedores.columns().get(3).type());
        assertEquals("0", devedores.columns().get(3).defaultValue());
        assertEquals(1, schema.relations().size());
    }

    @Test
    void generatesDdlForEachDialect() {
        Schema schema = importer.parse(ORACLE_DDL);

        String mysql = generator.generate(schema, "mysql");
        assertTrue(mysql.contains("`LOTE` varchar(20)"));
        assertTrue(mysql.contains("PRIMARY KEY (`ID`)"));
        assertTrue(mysql.contains("FOREIGN KEY (`CNPJ_CPF_EMITENTE`) REFERENCES `SOCITA_IE` (`CNPJ_CPF_EMITENTE`)"));

        String oracle = generator.generate(schema, "oracle");
        assertTrue(oracle.contains("LOTE varchar2(20)"));
        assertTrue(oracle.contains("ALTER TABLE DIFAL_DEVEDORES ADD CONSTRAINT"));
    }
}
