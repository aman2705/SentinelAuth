# SentinelAuth
SentinelAuth is a Spring Boot–based auth system using JWT, refresh tokens, and RBAC. After login, all auth events (login, token refresh, failures) are published to Kafka and consumed by downstream services for monitoring and security analytics. Dockerized microservices enable scalable, modular deployment.
