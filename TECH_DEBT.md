## Before production / team scaling

### Module boundary violations
- [ ] RefreshToken, UsageMetrics, AuditLog, Notification, Document have relationship with User and Tenant(look at the flyway sql files).
    Inside entities, the relationship aren't mention, due to the adaption for the modular monolith structure of this project 

### Other deferred items
- [ ] Add gRPC between Spring Boot and FastAPI
- [ ] Encrypt BYOK API keys at rest
- [ ] Add rate limiting on public endpoints