# KEYSTONE: Field Service Management Platform

Spring Boot 3 + PostgreSQL backend, React + TypeScript (Vite) frontend.

## Run locally
1. `docker compose up -d`          # PostgreSQL on :5432
2. `cd backend && mvn spring-boot:run`   # API on :8080 (Java 17+)
3. `cd frontend && npm install && npm run dev`   # UI on http://localhost:5173

Demo logins (seeded on first start, change them): admin/admin123, dispatch/dispatch123, tech1/tech123, customer1/cust123

## Features
- JWT auth with roles: ADMIN, DISPATCHER, TECHNICIAN, CUSTOMER
- Work order lifecycle: OPEN > ASSIGNED > IN_PROGRESS > (ON_HOLD) > COMPLETED > CLOSED, enforced server-side
- Dispatching, time tracking, parts tracking
- SLA deadlines by priority (Critical 4h, High 8h, Medium 24h, Low 72h) with breach and compliance metrics
- Dashboard for staff, customer self-service portal (register, raise and track own requests)

## API
POST /api/auth/login, /api/auth/register | GET/POST /api/work-orders | GET /api/work-orders/{id}
POST /api/work-orders/{id}/assign|status|time|parts | GET /api/dashboard, /api/technicians

## Next steps
Split Models/Controllers into one class per file, add Flyway migrations, tests, pagination, notifications, admin user management, a work order detail page.
