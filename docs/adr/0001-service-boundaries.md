# ADR 0001: Initial service boundaries

Status: Accepted

The system begins with three application deployables: Next.js web, Spring Boot core API, and a FastAPI AI/simulation service. Civilization code remains inside the Python simulation package and Spring domain modules rather than becoming another microservice. This limits distributed-system overhead while preserving contract boundaries.

