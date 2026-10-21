import type { DbType, Project, Schema, User } from './types';

const TOKEN_KEY = 'token';

export type OracleConnection = {
  host: string;
  port: number;
  service: string;
  sid: boolean;
  user: string;
  password: string;
  schema: string;
};

export const auth = {
  get token() {
    return localStorage.getItem(TOKEN_KEY);
  },
  set(token: string) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY);
  },
};

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`);

  const res = await fetch(`/api${path}`, { ...options, headers });
  if (res.status === 401 && !path.startsWith('/auth/login') && !path.startsWith('/auth/register')) {
    auth.clear();
    window.location.href = '/login';
  }
  if (!res.ok) {
    let message = `Erro ${res.status}`;
    try {
      const body = await res.json();
      message = body.message || body.error || message;
    } catch {
      /* corpo sem JSON */
    }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('content-type') ?? '';
  return (type.includes('json') ? res.json() : res.text()) as Promise<T>;
}

const json = (body: unknown) => JSON.stringify(body);

export const api = {
  login: (email: string, password: string) =>
    request<{ token: string; user: User }>('/auth/login', { method: 'POST', body: json({ email, password }) }),
  register: (name: string, email: string, password: string) =>
    request<{ token: string; user: User }>('/auth/register', { method: 'POST', body: json({ name, email, password }) }),
  me: () => request<User>('/auth/me'),

  listProjects: () => request<Project[]>('/projects'),
  createProject: (name: string, dbType: DbType) =>
    request<Project>('/projects', { method: 'POST', body: json({ name, dbType }) }),
  getProject: (id: string) => request<Project>(`/projects/${id}`),
  getShared: (token: string) => request<Project>(`/public/${token}`),
  updateProject: (id: string, patch: { name?: string; dbType?: DbType; schema?: Schema }) =>
    request<Project>(`/projects/${id}`, { method: 'PUT', body: json(patch) }),
  deleteProject: (id: number) => request<void>(`/projects/${id}`, { method: 'DELETE' }),
  cloneProject: (id: number) => request<Project>(`/projects/${id}/clone`, { method: 'POST' }),
  share: (id: string) => request<{ shareToken: string }>(`/projects/${id}/share`, { method: 'POST' }),
  unshare: (id: string) => request<void>(`/projects/${id}/share`, { method: 'DELETE' }),

  ddl: (dbType: DbType, schema: Schema) => request<string>('/ddl', { method: 'POST', body: json({ dbType, schema }) }),
  importDdl: (sql: string) => request<Schema>('/import/ddl', { method: 'POST', body: json({ sql }) }),
  importOracle: (conn: OracleConnection) => request<Schema>('/import/jdbc', { method: 'POST', body: json(conn) }),
};
