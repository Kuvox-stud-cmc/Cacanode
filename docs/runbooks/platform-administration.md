# Platform administration

Platform REST APIs and the web surface are disabled by default. Deploy the schema and application first, then seed the first administrator from an interactive terminal.

Local development:

```sh
cd api
make seed-platform-admin
```

Production Compose:

```sh
docker compose -f docker-compose.prod.yml run --rm -it business-api \
  --app.command.mode=seed-platform-admin --server.port=0 \
  --spring.task.scheduling.enabled=false
```

The command accepts no password arguments or environment variables. It prompts twice with terminal echo disabled, creates the internal tenant and first administrator atomically, and exits. Re-running it for the same active account is a no-op.

After seeding, enable `PLATFORM_ADMINISTRATION_ENABLED=true` on the API and `NEXT_PUBLIC_PLATFORM_ADMINISTRATION_ENABLED=true` on the frontend in that order.
