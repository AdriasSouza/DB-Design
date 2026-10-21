import { MarkerType, type Edge } from '@xyflow/react';
import type { Column, Schema, TableNodeType } from './types';

export const COLORS = ['#3182ce', '#e9726b', '#38a169', '#d69e2e', '#805ad5', '#319795', '#718096'];

export const uid = (prefix: string) => `${prefix}_${Math.random().toString(36).slice(2, 10)}`;

export const newColumn = (name = 'campo'): Column => ({
  id: uid('c'),
  name,
  type: 'varchar2(255)',
  pk: false,
  nullable: true,
  unique: false,
});

export const newTable = (x: number, y: number, count: number): TableNodeType => ({
  id: uid('t'),
  type: 'table',
  position: { x, y },
  data: {
    name: `TABELA_${count + 1}`,
    color: COLORS[count % COLORS.length],
    columns: [{ ...newColumn('ID'), type: 'integer', pk: true, nullable: false }],
  },
});

// Handles seguem o padrão "<idDaColuna>-<lado>", onde lado é "l" (esquerda) ou "r" (direita).
const columnOfHandle = (handle: string | null | undefined) => (handle ? handle.slice(0, handle.lastIndexOf('-')) : '');

export function toNodes(schema: Schema): TableNodeType[] {
  return schema.tables.map((t) => ({
    id: t.id,
    type: 'table',
    position: { x: t.x, y: t.y },
    data: { name: t.name, color: t.color || COLORS[0], columns: t.columns },
  }));
}

export function toEdges(schema: Schema): Edge[] {
  return schema.relations.map((r) => ({
    id: r.id,
    source: r.sourceTable,
    target: r.targetTable,
    sourceHandle: r.sourceHandle ?? `${r.sourceColumn}-r`,
    targetHandle: r.targetHandle ?? `${r.targetColumn}-l`,
    markerEnd: { type: MarkerType.ArrowClosed, color: '#e9726b' },
    style: { stroke: '#e9726b', strokeWidth: 1.5 },
  }));
}

export function toSchema(nodes: TableNodeType[], edges: Edge[]): Schema {
  return {
    tables: nodes.map((n) => ({
      id: n.id,
      name: n.data.name,
      color: n.data.color,
      x: Math.round(n.position.x),
      y: Math.round(n.position.y),
      columns: n.data.columns,
    })),
    relations: edges.map((e) => ({
      id: e.id,
      sourceTable: e.source,
      sourceColumn: columnOfHandle(e.sourceHandle),
      sourceHandle: e.sourceHandle ?? undefined,
      targetTable: e.target,
      targetColumn: columnOfHandle(e.targetHandle),
      targetHandle: e.targetHandle ?? undefined,
    })),
  };
}

export const columnOf = columnOfHandle;

/** Organiza as tabelas em grade, respeitando a altura de cada uma. */
export function autoLayout(nodes: TableNodeType[]): TableNodeType[] {
  const perRow = Math.max(1, Math.ceil(Math.sqrt(nodes.length)));
  const rowHeights: number[] = [];
  nodes.forEach((n, i) => {
    const row = Math.floor(i / perRow);
    rowHeights[row] = Math.max(rowHeights[row] ?? 0, 40 + n.data.columns.length * 24);
  });
  return nodes.map((n, i) => {
    const row = Math.floor(i / perRow);
    const y = rowHeights.slice(0, row).reduce((sum, h) => sum + h + 60, 0);
    return { ...n, position: { x: (i % perRow) * 300, y } };
  });
}
