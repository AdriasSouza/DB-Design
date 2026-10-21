import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, auth } from '../api';
import type { DbType, Project } from '../types';

const DB_LABEL: Record<DbType, string> = { oracle: 'Oracle', mysql: 'MySQL', postgres: 'PostgreSQL' };

export default function Dashboard() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [name, setName] = useState('');
  const [dbType, setDbType] = useState<DbType>('oracle');
  const [error, setError] = useState('');

  const load = () => api.listProjects().then(setProjects).catch((e) => setError(e.message));
  useEffect(() => {
    load();
  }, []);

  async function create(e: FormEvent) {
    e.preventDefault();
    const project = await api.createProject(name, dbType);
    navigate(`/projects/${project.id}`);
  }

  async function remove(p: Project) {
    if (!confirm(`Excluir o projeto "${p.name}"?`)) return;
    await api.deleteProject(p.id);
    load();
  }

  async function clone(p: Project) {
    await api.cloneProject(p.id);
    load();
  }

  function logout() {
    auth.clear();
    navigate('/login');
  }

  return (
    <div className="dashboard">
      <header className="topbar">
        <strong>DB DESIGNER</strong>
        <button className="btn ghost" onClick={logout}>Sair</button>
      </header>
      <main>
        <h2>Meus projetos</h2>
        <form className="card new-project" onSubmit={create}>
          <input placeholder="Nome do novo projeto" value={name} onChange={(e) => setName(e.target.value)} required />
          <select value={dbType} onChange={(e) => setDbType(e.target.value as DbType)}>
            {Object.entries(DB_LABEL).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <button className="btn primary" type="submit">+ Novo projeto</button>
        </form>
        {error && <div className="error">{error}</div>}
        {projects.map((p) => (
          <div className="card project-row" key={p.id}>
            <div>
              <small className="muted">Projeto</small>
              <Link to={`/projects/${p.id}`} className="project-name">{p.name}</Link>
            </div>
            <div>
              <small className="muted">Banco</small>
              <div>{DB_LABEL[p.dbType]}</div>
            </div>
            <div>
              <small className="muted">Tabelas / campos</small>
              <div>{p.tableCount} / {p.fieldCount}</div>
            </div>
            <div>
              <small className="muted">Atualizado</small>
              <div>{new Date(p.updatedAt).toLocaleString('pt-BR')}</div>
            </div>
            <div className="row-actions">
              <button className="btn ghost" onClick={() => navigate(`/projects/${p.id}`)}>Abrir</button>
              <button className="btn ghost" onClick={() => clone(p)}>Clonar</button>
              <button className="btn ghost danger" onClick={() => remove(p)}>Excluir</button>
            </div>
          </div>
        ))}
        {projects.length === 0 && <p className="muted">Nenhum projeto ainda. Crie o primeiro acima.</p>}
      </main>
    </div>
  );
}
