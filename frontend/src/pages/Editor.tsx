import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  addEdge,
  Background,
  ConnectionMode,
  Controls,
  getNodesBounds,
  getViewportForBounds,
  MarkerType,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
} from '@xyflow/react';
import { toPng, toSvg } from 'html-to-image';
import { api, type OracleConnection } from '../api';
import TableNode from '../components/TableNode';
import TablePanel from '../components/TablePanel';
import { autoLayout, columnOf, newTable, toEdges, toNodes, toSchema, uid } from '../schemaFlow';
import type { DbType, Schema, TableData, TableNodeType } from '../types';

const nodeTypes = { table: TableNode };
type Modal = 'ddl' | 'import' | 'share' | null;
type SaveState = 'saved' | 'saving' | 'error';

function download(filename: string, href: string) {
  const a = document.createElement('a');
  a.download = filename;
  a.href = href;
  a.click();
}

function EditorInner() {
  const { id, token } = useParams();
  const readOnly = !!token;
  const { screenToFlowPosition, getNodes, fitView } = useReactFlow<TableNodeType>();

  const [name, setName] = useState('');
  const [dbType, setDbType] = useState<DbType>('oracle');
  const [shareToken, setShareToken] = useState<string | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<TableNodeType>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [modal, setModal] = useState<Modal>(null);
  const [saveState, setSaveState] = useState<SaveState>('saved');
  const [error, setError] = useState('');
  const loaded = useRef(false);
  const lastSaved = useRef('');

  useEffect(() => {
    const request = token ? api.getShared(token) : api.getProject(id!);
    request
      .then((p) => {
        const schema: Schema = p.schema ?? { tables: [], relations: [] };
        setName(p.name);
        setDbType(p.dbType);
        setShareToken(p.shareToken);
        setNodes(toNodes(schema));
        setEdges(toEdges(schema));
        lastSaved.current = JSON.stringify({ name: p.name, dbType: p.dbType, schema: toSchema(toNodes(schema), toEdges(schema)) });
        loaded.current = true;
        setTimeout(() => fitView({ padding: 0.2 }), 50);
      })
      .catch((e) => setError(e.message));
  }, [id, token, setNodes, setEdges, fitView]);

  // Autosave com debounce; só envia quando o conteúdo realmente mudou (seleção/medição não contam).
  const payload = useMemo(
    () => JSON.stringify({ name, dbType, schema: toSchema(nodes, edges) }),
    [name, dbType, nodes, edges],
  );
  useEffect(() => {
    if (readOnly || !loaded.current || payload === lastSaved.current) return;
    setSaveState('saving');
    const timer = setTimeout(() => {
      api
        .updateProject(id!, JSON.parse(payload))
        .then(() => {
          lastSaved.current = payload;
          setSaveState('saved');
        })
        .catch(() => setSaveState('error'));
    }, 1000);
    return () => clearTimeout(timer);
  }, [payload, id, readOnly]);

  const selected = nodes.find((n) => n.selected);

  const onConnect = useCallback(
    (c: Connection) =>
      setEdges((eds) =>
        addEdge(
          {
            ...c,
            id: uid('r'),
            markerEnd: { type: MarkerType.ArrowClosed, color: '#e9726b' },
            style: { stroke: '#e9726b', strokeWidth: 1.5 },
          },
          eds,
        ),
      ),
    [setEdges],
  );

  const addTable = () => {
    const pos = screenToFlowPosition({ x: window.innerWidth / 2, y: window.innerHeight / 2 });
    setNodes((nds) => [...nds.map((n) => ({ ...n, selected: false })), { ...newTable(pos.x, pos.y, nds.length), selected: true }]);
  };

  const patchSelected = (patch: Partial<TableData>) =>
    setNodes((nds) => nds.map((n) => (n.selected ? { ...n, data: { ...n.data, ...patch } } : n)));

  const removeColumn = (columnId: string) => {
    patchSelected({ columns: selected!.data.columns.filter((c) => c.id !== columnId) });
    setEdges((eds) => eds.filter((e) => columnOf(e.sourceHandle) !== columnId && columnOf(e.targetHandle) !== columnId));
  };

  const deleteSelected = () => {
    const tableId = selected!.id;
    setNodes((nds) => nds.filter((n) => n.id !== tableId));
    setEdges((eds) => eds.filter((e) => e.source !== tableId && e.target !== tableId));
  };

  const arrange = () => {
    setNodes((nds) => autoLayout(nds));
    setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 50);
  };

  const exportImage = async (format: 'png' | 'svg') => {
    const el = document.querySelector<HTMLElement>('.react-flow__viewport');
    if (!el || nodes.length === 0) return;
    const bounds = getNodesBounds(getNodes());
    const width = bounds.width + 100;
    const height = bounds.height + 100;
    const vp = getViewportForBounds(bounds, width, height, 0.2, 2, 0.05);
    const options = {
      backgroundColor: '#ffffff',
      width,
      height,
      style: { width: `${width}px`, height: `${height}px`, transform: `translate(${vp.x}px, ${vp.y}px) scale(${vp.zoom})` },
    };
    const href = format === 'png' ? await toPng(el, options) : await toSvg(el, options);
    download(`${name || 'diagrama'}.${format}`, href);
  };

  const replaceSchema = (schema: Schema) => {
    setNodes(toNodes(schema));
    setEdges(toEdges(schema));
    setTimeout(() => fitView({ padding: 0.2 }), 50);
  };

  if (error) {
    return <div className="center-message"><p className="error">{error}</p><Link to="/">Voltar</Link></div>;
  }

  return (
    <div className="editor">
      <header className="topbar">
        {!readOnly && <Link to="/" className="btn ghost">← Projetos</Link>}
        {readOnly ? (
          <strong>{name} <span className="muted">(somente leitura)</span></strong>
        ) : (
          <input className="project-title" value={name} onChange={(e) => setName(e.target.value)} />
        )}
        {!readOnly && (
          <select value={dbType} onChange={(e) => setDbType(e.target.value as DbType)}>
            <option value="oracle">Oracle</option>
            <option value="mysql">MySQL</option>
            <option value="postgres">PostgreSQL</option>
          </select>
        )}
        <span className="spacer" />
        {!readOnly && <button className="btn" onClick={addTable}>+ Tabela</button>}
        {!readOnly && <button className="btn" onClick={arrange}>Auto-layout</button>}
        {!readOnly && <button className="btn" onClick={() => setModal('import')}>Importar</button>}
        {!readOnly && <button className="btn" onClick={() => setModal('ddl')}>Exportar DDL</button>}
        <button className="btn" onClick={() => exportImage('png')}>PNG</button>
        <button className="btn" onClick={() => exportImage('svg')}>SVG</button>
        {!readOnly && <button className="btn" onClick={() => setModal('share')}>Compartilhar</button>}
        {!readOnly && (
          <span className={`save-state ${saveState}`}>
            {saveState === 'saved' ? 'Salvo' : saveState === 'saving' ? 'Salvando…' : 'Erro ao salvar'}
          </span>
        )}
      </header>

      <div className="workspace">
        {!readOnly && (
          <aside className="side-panel">
            {selected ? (
              <TablePanel
                data={selected.data}
                dbType={dbType}
                onChange={patchSelected}
                onRemoveColumn={removeColumn}
                onDeleteTable={deleteSelected}
              />
            ) : (
              <p className="muted pad">Selecione uma tabela para editar, ou clique em “+ Tabela”. Arraste de um ponto de conexão de uma coluna até outra para criar uma relação (FK).</p>
            )}
          </aside>
        )}
        <div className="canvas">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            connectionMode={ConnectionMode.Loose}
            nodesDraggable={!readOnly}
            nodesConnectable={!readOnly}
            elementsSelectable={!readOnly}
            deleteKeyCode={readOnly ? null : ['Backspace', 'Delete']}
            snapToGrid
            snapGrid={[10, 10]}
            minZoom={0.1}
            fitView
          >
            <Background gap={20} />
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable nodeColor={(n) => (n.data as TableData).color} />
          </ReactFlow>
        </div>
      </div>

      {modal === 'ddl' && <DdlModal dbType={dbType} schema={toSchema(nodes, edges)} name={name} onClose={() => setModal(null)} />}
      {modal === 'import' && (
        <ImportModal
          hasContent={nodes.length > 0}
          onImport={(schema) => {
            replaceSchema(schema);
            setModal(null);
          }}
          onClose={() => setModal(null)}
        />
      )}
      {modal === 'share' && (
        <ShareModal projectId={id!} token={shareToken} onChange={setShareToken} onClose={() => setModal(null)} />
      )}
    </div>
  );
}

function ModalShell({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal card" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="btn ghost" onClick={onClose}>×</button>
        </div>
        {children}
      </div>
    </div>
  );
}

function DdlModal({ dbType, schema, name, onClose }: { dbType: DbType; schema: Schema; name: string; onClose: () => void }) {
  const [target, setTarget] = useState<DbType>(dbType);
  const [sql, setSql] = useState('');
  useEffect(() => {
    api.ddl(target, schema).then(setSql).catch((e) => setSql(`-- ${e.message}`));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  return (
    <ModalShell title="Exportar DDL" onClose={onClose}>
      <select value={target} onChange={(e) => setTarget(e.target.value as DbType)}>
        <option value="oracle">Oracle</option>
        <option value="mysql">MySQL</option>
        <option value="postgres">PostgreSQL</option>
      </select>
      <textarea readOnly value={sql} rows={16} />
      <div className="modal-actions">
        <button className="btn" onClick={() => navigator.clipboard.writeText(sql)}>Copiar</button>
        <button
          className="btn primary"
          onClick={() => download(`${name || 'schema'}.sql`, `data:text/plain;charset=utf-8,${encodeURIComponent(sql)}`)}
        >
          Baixar .sql
        </button>
      </div>
    </ModalShell>
  );
}

function ImportModal({ hasContent, onImport, onClose }: { hasContent: boolean; onImport: (s: Schema) => void; onClose: () => void }) {
  const [tab, setTab] = useState<'ddl' | 'oracle'>('ddl');
  const [sql, setSql] = useState('');
  const [conn, setConn] = useState<OracleConnection>({
    host: '', port: 1521, service: '', sid: false, user: '', password: '', schema: '',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const setField = <K extends keyof OracleConnection>(key: K, value: OracleConnection[K]) =>
    setConn((c) => ({ ...c, [key]: value }));
  const ready = tab === 'ddl' ? !!sql.trim() : !!(conn.host && conn.service && conn.user && conn.password);

  async function run() {
    if (hasContent && !confirm('Isso substituirá o diagrama atual. Continuar?')) return;
    setError('');
    setBusy(true);
    try {
      onImport(tab === 'ddl' ? await api.importDdl(sql) : await api.importOracle(conn));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <ModalShell title="Importar" onClose={onClose}>
      <div className="tabs">
        <button className={tab === 'ddl' ? 'tab active' : 'tab'} onClick={() => setTab('ddl')}>Script DDL</button>
        <button className={tab === 'oracle' ? 'tab active' : 'tab'} onClick={() => setTab('oracle')}>Banco Oracle</button>
      </div>
      {tab === 'ddl' ? (
        <>
          <p className="muted">Cole scripts CREATE TABLE / ALTER TABLE … FOREIGN KEY (Oracle, MySQL ou PostgreSQL).</p>
          <textarea value={sql} onChange={(e) => setSql(e.target.value)} rows={16} placeholder="CREATE TABLE ..." />
        </>
      ) : (
        <>
          <p className="muted">
            Lê tabelas, colunas e chaves do catálogo do Oracle. A senha é usada só nesta importação e não é armazenada.
            Prefira um usuário somente leitura.
          </p>
          <div className="form-grid">
            <label>Host<input value={conn.host} onChange={(e) => setField('host', e.target.value)} /></label>
            <label>Porta<input type="number" value={conn.port} onChange={(e) => setField('port', Number(e.target.value))} /></label>
            <label>
              {conn.sid ? 'SID' : 'Service name'}
              <input value={conn.service} onChange={(e) => setField('service', e.target.value)} />
            </label>
            <label className="check">
              <input type="checkbox" checked={conn.sid} onChange={(e) => setField('sid', e.target.checked)} /> Usar SID
            </label>
            <label>Usuário<input value={conn.user} onChange={(e) => setField('user', e.target.value)} autoComplete="off" /></label>
            <label>Senha<input type="password" value={conn.password} onChange={(e) => setField('password', e.target.value)} autoComplete="new-password" /></label>
            <label>Schema (opcional)<input value={conn.schema} placeholder="padrão: o usuário" onChange={(e) => setField('schema', e.target.value)} /></label>
          </div>
        </>
      )}
      {error && <div className="error">{error}</div>}
      <div className="modal-actions">
        <button className="btn primary" disabled={!ready || busy} onClick={run}>{busy ? 'Importando…' : 'Importar'}</button>
      </div>
    </ModalShell>
  );
}

function ShareModal({ projectId, token, onChange, onClose }: { projectId: string; token: string | null; onChange: (t: string | null) => void; onClose: () => void }) {
  const link = token ? `${window.location.origin}/s/${token}` : '';
  return (
    <ModalShell title="Compartilhar" onClose={onClose}>
      <p className="muted">Qualquer pessoa com o link pode visualizar o diagrama (somente leitura), sem login.</p>
      {token ? (
        <>
          <input readOnly value={link} onFocus={(e) => e.target.select()} />
          <div className="modal-actions">
            <button className="btn" onClick={() => navigator.clipboard.writeText(link)}>Copiar link</button>
            <button className="btn ghost danger" onClick={() => api.unshare(projectId).then(() => onChange(null))}>Desativar link</button>
          </div>
        </>
      ) : (
        <div className="modal-actions">
          <button className="btn primary" onClick={() => api.share(projectId).then((r) => onChange(r.shareToken))}>Gerar link</button>
        </div>
      )}
    </ModalShell>
  );
}

export default function Editor() {
  return (
    <ReactFlowProvider>
      <EditorInner />
    </ReactFlowProvider>
  );
}
