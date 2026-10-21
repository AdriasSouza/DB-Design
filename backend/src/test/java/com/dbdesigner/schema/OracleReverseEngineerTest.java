package com.dbdesigner.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleReverseEngineerTest {

    @Test
    void formatsCatalogTypes() {
        assertEquals("varchar2(50)", OracleReverseEngineer.formatType("VARCHAR2", 50, null, null));
        assertEquals("number", OracleReverseEngineer.formatType("NUMBER", 22, null, null));
        assertEquals("number(10)", OracleReverseEngineer.formatType("NUMBER", 22, 10, 0));
        assertEquals("number(12,2)", OracleReverseEngineer.formatType("NUMBER", 22, 12, 2));
        assertEquals("timestamp(6)", OracleReverseEngineer.formatType("TIMESTAMP(6)", 0, null, null));
        assertEquals("date", OracleReverseEngineer.formatType("DATE", 0, null, null));
    }
}
