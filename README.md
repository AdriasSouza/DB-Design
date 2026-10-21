# DB Designer

Editor de diagramas ER (estilo DB Designer / dbdiagram) com backend **Spring Boot 3** e frontend **React + TypeScript + React Flow**.

## Executar

Requisitos: JDK 17+, Maven, Node 20+.

```bash
# backend (http://localhost:8080) — banco H2 em arquivo: backend/data/
cd backend
mvn spring-boot:run

# frontend (http://localhost:5173) — faz proxy de /api para o backend
cd frontend
npm install
npm run dev
```

Em produção defina `JWT_SECRET` (mín. 32 caracteres) e `CORS_ORIGINS`.

## O que existe

| Recurso | Onde |
|---|---|
| Canvas com tabelas (custom node) e relações entre colunas (handles por coluna) | `frontend/src/pages/Editor.tsx`, `components/TableNode.tsx` |
| Painel lateral: editar tabela, cor, colunas, PK / NOT NULL / UNIQUE | `components/TablePanel.tsx` |
| CRUD de projetos + clonar, salvamento automático | `backend/.../web/ProjectController.java` |
| Cadastro/login com JWT | `backend/.../web/AuthController.java`, `security/` |
| Exportar PNG / SVG | Editor (html-to-image) |
| Gerar DDL Oracle / MySQL / PostgreSQL | `backend/.../schema/DdlGenerator.java` |
| Importar DDL (CREATE TABLE, ALTER TABLE … FOREIGN KEY) | `backend/.../schema/DdlImporter.java` |
| Auto-layout (grade) | `frontend/src/schemaFlow.ts` |
| Link público somente leitura (`/s/:token`) | `ProjectController#shared` |

O diagrama é guardado como JSON na coluna `schema_json` do projeto. Origem de uma relação = coluna FK; destino = coluna referenciada.

## Próximos passos

- Engenharia reversa via JDBC (ler `ALL_TAB_COLUMNS` / `ALL_CONSTRAINTS` no Oracle).
- Trocar H2 por PostgreSQL com `jsonb` (basta mudar o datasource e o tipo da coluna).
- Markup em texto (DSL) sincronizado com o canvas; auto-layout com dagre/elk.
- Compartilhamento com membros por e-mail e permissões.
