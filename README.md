# BrandLens

Brand governance review for the fictional **LumaLeaf** brand. Every finding cites its source; every change remains a human decision.

## Judge demo

Start the app, open the workspace, choose **Load sample draft**, then **Run review**. Inspect the cited source on each finding, accept one suggestion, dismiss another with a reason, and escalate a third. Reload the saved review from History to see the persisted decisions. The sample includes a prohibited term, a conflicting fact, an unsupported claim, and a subjective opinion. Seed content is fictional.

## Architecture

React + TypeScript + Vite frontend (`frontend/`) calls a Java 21 / Spring Boot REST API (`backend/`). Spring Data JPA persists rules, approved facts, reviews, findings, and audit events to PostgreSQL. The current review pipeline runs deterministic terminology and fact checks. Semantic AI analysis is deferred; the app reports AI checks unavailable even if AI-related environment placeholders are populated. Deterministic checks remain available.

## Run locally

Prerequisites: Java 21, Maven 3.9+, Node 20+, npm, Docker Compose.

1. Copy `.env.example` to `.env` and set values as needed. For the default local app profile, start only PostgreSQL with `docker compose up -d db`.
2. Backend: `cd backend; mvn spring-boot:run` (Spring creates the schema and seeds fictional data on first startup).
3. Frontend in another terminal: `cd frontend; npm ci; npm run dev` and open http://localhost:5173. The committed lockfile keeps local and Docker installs aligned.

Or `docker compose up --build` runs PostgreSQL, API, and built frontend. The UI is exposed on port 8080. API health is `/api/health`; API docs are `/swagger-ui.html`.

## Configuration

See `.env.example`. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` configure PostgreSQL. The `AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL` placeholders are reserved for the deferred semantic adapter and currently have no effect. Draft content is not sent to an external AI provider by this implementation.

## Build and tests

`cd backend; mvn test package`

`cd frontend; npm ci; npm test; npm run build`

## Deployment

Deploy the frontend and backend independently if desired: build frontend with `VITE_API_BASE_URL` set to the backend origin, and deploy the Spring Boot executable JAR with a managed PostgreSQL database and environment variables above. Set `CORS_ALLOWED_ORIGINS` to the deployed frontend origin. For same-origin Docker deployment, the frontend container serves the built UI and proxies `/api` to the API service. No public deployment has been performed.

## Limitations and roadmap

The demo role is unauthenticated and intended for fictional public demo content; add access control before using real customer material. AI-assisted analysis and model-output validation are deferred. The current quantitative unsupported-claim check is intentionally narrow. Alt-text/image analysis, editable rule/fact forms, and production identity management are deferred.

Planned Round 3 improvements: (1) intent-preserving span diffs, new-claim validation, and acceptance-quality evaluation; (2) guideline intelligence from dismissals and escalations; (3) visual governance for logos, palettes, and sourced image findings.
