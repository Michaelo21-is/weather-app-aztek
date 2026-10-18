# Weather App

Current weather and a 5-day forecast for your location or any city, with accounts and saved favorite locations.

**Live:** https://michael-site.com

| Part | Stack | Docs |
|---|---|---|
| `frontend/` | Next.js 16, React 19, Tailwind CSS | [frontend/README.md](frontend/README.md) |
| `backend/` | Spring Boot 4, PostgreSQL (Flyway), Redis, JWT cookies, OpenWeather | [backend/README.md](backend/README.md) |
| `redis/` | Redis sidecar image for App Service | [docs/azure-deployment.md](docs/azure-deployment.md) |
| `infra/` | Terraform (Azure), deployment and test scripts | [docs/azure-deployment.md](docs/azure-deployment.md) |
| `.github/workflows/` | CI/CD: tests, image builds, deployment to Azure | [docs/azure-deployment.md](docs/azure-deployment.md#cicd) |

Hosting: Azure App Service (North Europe) behind Cloudflare, provisioned with Terraform and deployed by GitHub Actions.
Architecture, costs, security and operations: [docs/azure-deployment.md](docs/azure-deployment.md).
