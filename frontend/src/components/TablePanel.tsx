import type { Column, DbType, TableData } from '../types';
import { COLORS, newColumn } from '../schemaFlow';

const TYPES: Record<DbType, string[]> = {
  oracle: ['varchar2(255)', 'number', 'number(10)', 'number(12,2)', 'integer', 'date', 'timestamp', 'clob', 'blob', 'char(1)'],
  mysql: ['varchar(255)', 'int', 'bigint', 'decimal(12,2)', 'date', 'datetime', 'text', 'longtext', 'tinyint(1)', 'blob'],
  postgres: ['varchar(255)', 'integer', 'bigint', 'numeric(12,2)', 'date', 'timestamp', 'text', 'boolean', 'uuid', 'jsonb'],
};

type Props = {
  data: TableData;
  dbType: DbType;
  onChange: (patch: Partial<TableData>) => void;
  onRemoveColumn: (columnId: string) => void;
  onDeleteTable: () => void;
};

export default function TablePanel({ data, dbType, onChange, onRemoveColumn, onDeleteTable }: Props) {
  const updateColumn = (id: string, patch: Partial<Column>) =>
    onChange({ columns: data.columns.map((c) => (c.id === id ? { ...c, ...patch } : c)) });

  return (
    <div className="panel-body">
      <label>
        Tabela
        <input value={data.name} onChange={(e) => onChange({ name: e.target.value })} />
      </label>
      <div className="swatches">
        {COLORS.map((color) => (
          <button
            key={color}
            className={color === data.color ? 'swatch active' : 'swatch'}
            style={{ background: color }}
            onClick={() => onChange({ color })}
            aria-label={`Cor ${color}`}
          />
        ))}
      </div>

      <datalist id="column-types">
        {TYPES[dbType].map((t) => <option key={t} value={t} />)}
      </datalist>

      <div className="col-head">
        <span>Colunas</span>
        <span title="Chave primária">PK</span>
        <span title="Não nulo">NN</span>
        <span title="Único">UQ</span>
        <span />
      </div>
      {data.columns.map((c) => (
        <div className="col-edit" key={c.id}>
          <input value={c.name} onChange={(e) => updateColumn(c.id, { name: e.target.value })} />
          <input list="column-types" value={c.type} onChange={(e) => updateColumn(c.id, { type: e.target.value })} />
          <input
            type="checkbox"
            checked={c.pk}
            onChange={(e) => updateColumn(c.id, { pk: e.target.checked, nullable: e.target.checked ? false : c.nullable })}
          />
          <input type="checkbox" checked={!c.nullable} onChange={(e) => updateColumn(c.id, { nullable: !e.target.checked })} />
          <input type="checkbox" checked={c.unique} onChange={(e) => updateColumn(c.id, { unique: e.target.checked })} />
          <button className="btn ghost danger" onClick={() => onRemoveColumn(c.id)} aria-label="Remover coluna">×</button>
        </div>
      ))}
      <button className="btn" onClick={() => onChange({ columns: [...data.columns, newColumn()] })}>+ Coluna</button>
      <button className="btn ghost danger" onClick={onDeleteTable}>Excluir tabela</button>
    </div>
  );
}
