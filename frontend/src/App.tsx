import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { auth } from './api';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Editor from './pages/Editor';

function RequireAuth({ children }: { children: ReactElement }) {
  return auth.token ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/s/:token" element={<Editor />} />
      <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
      <Route path="/projects/:id" element={<RequireAuth><Editor /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
