# ONNXI — платформа для инференса ONNX-моделей

Web-приложение: пользователь загружает свою ONNX-модель и датасет, ставит задачу
в очередь, получает результаты инференса и (если датасет размечен) метрики качества.

## Что внутри

- **4 Spring Boot 3.5 сервиса** на Java 21 в Maven multi-module:
  `gateway-service`, `backend-service`, `inference-worker`, `notification-service` +
  библиотека `shared-dto`.
- **Keycloak 26** как IdP/SSO (OIDC + JWT resource-server).
- **PostgreSQL 16** + Liquibase миграции.
- **MinIO** (S3-совместимое хранилище моделей, датасетов и результатов).
- **Apache Kafka** (KRaft) — топики `inference.tasks`, `inference.status`,
  `inference.notifications` + DLQ.
- **Redis** — Spring Session + bucket'ы Bucket4j для rate-limit.
- **Microsoft ONNX Runtime** (Java) — инференс прямо на JVM.
- **Prometheus + Grafana** — метрики, дашборд `ONNXI Overview`.
- **Caddy** — TLS, HSTS, CSP, маршруты `/auth/*` → Keycloak, остальное → gateway.
- **SMTP** — email-уведомления при завершении задач.

## Ключевые возможности

- Регистрация/вход через Keycloak (OIDC Authorization Code + PKCE, опционально social
  login). Сессия гейтвея хранится в Redis.
- Загрузка моделей (валидация .onnx через `OrtSession` в памяти) и ZIP-датасетов
  (плоских или в YOLO-формате `images/labels/{train,val}`).
- Постановка задачи: backend кладёт запись в Postgres и публикует сообщение в Kafka.
- Worker: скачивает артефакты из MinIO, гонит инференс, для YOLO авто-детектит формат
  (classic / e2e detect / pose) и применяет NMS + de-letterbox, считает recall и
  **PCK@5px** для keypoints. Для классификаторов — argmax + accuracy. Результат — CSV
  в MinIO + presigned URL для скачивания.
- Notifications: при завершении задачи backend публикует обогащённое событие;
  notification-service шлёт email согласно `/api/me/notifications`.
- HTMX-таблица задач с авто-обновлением каждые 3 секунды.

## Архитектура

См. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) для подробной диаграммы и потоков.

Кратко:

```
браузер ──https──> Caddy ──┬──> Keycloak (/auth/*)
                            └──> gateway ──JWT──> backend ──> Postgres
                                                       └────> MinIO
                                                       └────> Kafka ───> inference-worker
                                                                          │
                                                                          ▼
                                                                    notification-service
                                                                          │
                                                                          email
```

## Быстрый запуск (локально)

Требования: Docker Desktop / Docker Engine 26+, 6 GB+ свободной RAM.

```bash
git clone https://github.com/CRAZYS0K/A-platform-for-inference-on-the-side-of-ONNX-models.git onnxi
cd onnxi
cp docker/.env.example docker/.env             # при желании заполнить TELEGRAM_BOT_TOKEN
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d
```

Через ~60 секунд:

- UI: <https://localhost> (Caddy выпустит self-signed сертификат — первый раз
  браузер ругнётся, согласитесь).
- Keycloak admin: <https://localhost/auth> (логин `admin`/`admin`).
- MinIO Console: <https://localhost/minio> (`minioadmin`/`minioadmin`).
- Grafana: <http://localhost:3000> (если включён профиль `observability`).

### Регистрация и первый вход

1. Откройте <https://localhost>, нажмите «Войти» → откроется Keycloak.
2. Зарегистрируйтесь («Register»). Email обязателен — он сохранится в
   `users.email` при первом обращении к `/api/me`.
3. После входа вы попадёте на страницу профиля.
4. Загрузите модель на странице «Модели» (`.onnx`), датасет на странице «Датасеты»
   (`.zip`), создайте задачу на «Задачи».

## Тесты

```bash
./mvnw -B verify              # включая Testcontainers
./mvnw -Psecurity-scan verify # + OWASP Dependency-Check (генерит target/dependency-check-report.html)
```

Интеграционные тесты используют Testcontainers (Postgres + Keycloak + MinIO + Kafka).
Если Docker недоступен — тесты помечаются как `skipped` через `@EnabledIf`.

Структура тестов:
- **backend-service** — 6 интеграционных (auth flow, storage, task API, notification emit
  + 2 базовых).
- **inference-worker** — 10 юнит-тестов (YOLO parser, label parser, accuracy, dataset
  layout, container).
- **gateway-service / notification-service** — `contextLoads`.

## Структура репозитория

```
.
├── shared-dto/                     # DTO для Kafka сообщений
├── gateway-service/                # Thymeleaf UI + OAuth2 client + Bucket4j rate-limit
│                                   # + Resilience4j CircuitBreaker
├── backend-service/                # REST API + JPA + Liquibase + Kafka producer/consumer
├── inference-worker/               # Kafka consumer + MinIO + ONNX Runtime + YOLO parser
├── notification-service/           # Kafka consumer + JavaMail
├── docker/
│   ├── docker-compose.yml          # dev compose (self-signed TLS, Mailhog, in-mem Keycloak)
│   ├── docker-compose.prod.yml     # production overlay (GHCR images, mem limits, postgres-backed Keycloak)
│   ├── caddy/Caddyfile             # dev Caddyfile
│   ├── caddy/Caddyfile.prod        # prod Caddyfile (Let's Encrypt)
│   └── keycloak/realm-export.json  # realm + клиенты + роли + тестовый пользователь
├── infra/
│   ├── prometheus.yml
│   └── grafana/{provisioning,dashboards}/
├── .github/workflows/ci.yml        # build + test + push GHCR + OWASP DC
├── docs/
│   ├── ARCHITECTURE.md
│   ├── RUNBOOK.md                  # сквозной сценарий с YOLO для защиты
│   └── DEPLOY.md                   # деплой на VPS (на случай если появится сервер)
└── README.md (этот файл)
```

## Соответствие требованиям ТЗ

| Требование | Статус | Где смотреть |
|---|---|---|
| Backend на Java | ✓ | Java 21, Spring Boot 3.5.6 |
| Web + БД | ✓ | Thymeleaf + HTMX, PostgreSQL 16 |
| ≥ 2 сервиса | ✓ | 4 сервиса + Keycloak |
| Безопасность только через сторонние библиотеки | ✓ | Spring Security + Keycloak; своего криптокода нет |
| HTTPS | ✓ | Caddy (self-signed dev / Let's Encrypt prod) |

Бонусы (план — до 35 баллов):

| Бонус | Статус |
|---|---|
| CI/CD | ✓ GitHub Actions: test → push в GHCR → OWASP DC |
| Тесты с testcontainers | ✓ Postgres + Keycloak + MinIO + Kafka |
| Kafka / Redis / S3 | ✓ Все три |
| Контейнеризация | ✓ Multi-stage Dockerfile на каждый сервис + dev/prod compose |
| Observability | ✓ Micrometer + Prometheus + Grafana + JSON logs + correlationId |
| Внешние интеграции | ✓ Email (Spring Mail) |
| Хорошая регистрация/вход | ✓ Keycloak OIDC, опционально social login |
| Паттерны отказоустойчивости | ✓ Resilience4j: ExponentialBackOff + DLQ для Kafka, CircuitBreaker для HTTP |
| Качественная защита | ✓ Spring Security CSP/HSTS/CSRF, Bucket4j-Redis rate-limit, OWASP DC |
| Размещение на сервере с доменом | ✗ (нет VPS; конфигурация под прод готова — `docs/DEPLOY.md`) |

## Безопасность

Никаких самописных хешей и токенов — только сторонние библиотеки:

- **Spring Security 6** + `spring-boot-starter-oauth2-client/-resource-server`.
- **Keycloak 26** — пароли, MFA, social login, refresh.
- **Bucket4j 8** + **Lettuce** — distributed rate-limit на 60 req/min/user.
- **Caddy** — TLS, HSTS, security headers.
- **OWASP Dependency-Check 10** — сканирование зависимостей в CI.

CSP: `default-src 'self'`, скрипты только с `cdn.jsdelivr.net` (Bootstrap) и
`unpkg.com` (HTMX). Cookie сессии — `HttpOnly`, `SameSite=Lax`, `Secure=true` в проде.

## Известные ограничения

- Worker поддерживает только модели с одним input/output (типично для классификации и
  YOLO detect/pose); сегментация (с proto-mask вторым выходом) не разобрана.
- Авто-определение `num_classes` в YOLO non-e2e парсере — эвристика: 1, 2, 80. Для
  кастомного `nc` нужно либо переделать на явную метаданную в датасете, либо
  использовать e2e-экспорт модели.
- Размер модели/датасета ограничен 500 MB на upload (multipart-лимит).
- Testcontainers на Docker Desktop 29+ на Windows работают только из WSL2 (см.
  `docs/DEPLOY.md`); локально в Windows-нативном Docker тесты помечаются как skipped.

## Лицензия

Учебный проект, без лицензии.
