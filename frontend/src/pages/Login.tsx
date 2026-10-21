import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, auth } from '../api';

export default function Login() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    try {
      const res = mode === 'login' ? await api.login(email, password) : await api.register(name, email, password);
      auth.set(res.token);
      navigate('/');
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="login-page">
      <form className="card login-card" onSubmit={submit}>
        <h1>DB Designer</h1>
        <p className="muted">{mode === 'login' ? 'Entre na sua conta' : 'Crie sua conta'}</p>
        {mode === 'register' && (
          <input placeholder="Nome" value={name} onChange={(e) => setName(e.target.value)} required />
        )}
        <input type="email" placeholder="E-mail" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <input
          type="password"
          placeholder="Senha"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          minLength={6}
          required
        />
        {error && <div className="error">{error}</div>}
        <button className="btn primary" type="submit">
          {mode === 'login' ? 'Entrar' : 'Cadastrar'}
        </button>
        <button type="button" className="btn link" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'Criar uma conta' : 'Já tenho conta'}
        </button>
      </form>
    </div>
  );
}
