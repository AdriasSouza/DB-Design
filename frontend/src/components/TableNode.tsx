import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { TableNodeType } from '../types';

function TableNode({ data, selected }: NodeProps<TableNodeType>) {
  return (
    <div className={`table-node${selected ? ' selected' : ''}`}>
      <div className="table-header" style={{ background: data.color }}>{data.name}</div>
      {data.columns.map((c) => (
        <div className="table-row" key={c.id}>
          <Handle type="source" position={Position.Left} id={`${c.id}-l`} />
          <span className={c.pk ? 'col-name pk' : 'col-name'}>{c.name}</span>
          <span className="col-type">{c.type}</span>
          <Handle type="source" position={Position.Right} id={`${c.id}-r`} />
        </div>
      ))}
    </div>
  );
}

export default memo(TableNode);
