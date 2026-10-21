import type { Node } from '@xyflow/react';

export type DbType = 'oracle' | 'mysql' | 'postgres';

export type Column = {
  id: string;
  name: string;
  type: string;
  pk: boolean;
  nullable: boolean;
  unique: boolean;
  defaultValue?: string;
};

export type TableDef = {
  id: string;
  name: string;
  color: string;
  x: number;
  y: number;
  columns: Column[];
};

/** Origem = coluna que referencia (FK); destino = coluna referenciada (PK). */
export type Relation = {
  id: string;
  sourceTable: string;
  sourceColumn: string;
  sourceHandle?: string;
  targetTable: string;
  targetColumn: string;
  targetHandle?: string;
};

export type Schema = { tables: TableDef[]; relations: Relation[] };

export type Project = {
  id: number;
  name: string;
  dbType: DbType;
  updatedAt: string;
  tableCount: number;
  fieldCount: number;
  shareToken: string | null;
  schema?: Schema;
};

export type User = { id: number; email: string; name: string };

export type TableData = { name: string; color: string; columns: Column[] };
export type TableNodeType = Node<TableData, 'table'>;
