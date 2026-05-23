# Архитектура ONNXI

## Общая схема

```mermaid
graph LR
    User((Пользователь)) -->|HTTPS| Caddy
    subgraph Edge
        Caddy[Caddy<br/>TLS / HSTS / CSP]
    end
    Caddy --> Gateway
    Caddy -->|/auth/*| Keycloak

    subgraph Application
        Gateway[gateway-service<br/>Thymeleaf + OAuth2 client<br/>Bucket4j rate-limit<br/>CircuitBreaker]
        Backend[backend-service<br/>REST API<br/>oauth2-resource-server<br/>JPA + Liquibase]
        Worker[inference-worker<br/>Kafka consumer<br/>ONNX Runtime<br/>YOLO parser]
        Notif[notification-service<br/>Kafka consumer<br/>SMTP + Telegram]
    end

    Gateway -->|JWT| Backend
    Backend --> Postgres[(PostgreSQL)]
    Backend --> MinIO[(MinIO S3)]
    Backend -->|inference.tasks| Kafka{{Kafka}}
    Kafka -->|inference.tasks| Worker
    Worker --> MinIO
    Worker -->|inference.status| Kafka
    Kafka -->|inference.status| Backend
    Backend -->|inference.notifications| Kafka
    Kafka -->|inference.notifications| Notif
    Notif --> SMTP[(SMTP / Mailhog)]
    Notif --> Telegram[(Telegram Bot API)]

    Gateway --> Redis[(Redis<br/>session + buckets)]
    Keycloak --> Postgres

    subgraph Observability
        Prometheus[(Prometheus)]
        Grafana[(Grafana)]
    end

    Gateway -.metrics.-> Prometheus
    Backend -.metrics.-> Prometheus
    Worker -.metrics.-> Prometheus
    Notif -.metrics.-> Prometheus
    Prometheus --> Grafana
```

## Сервисы

| Сервис | Порт | Ответственность |
|---|---|---|
| `gateway-service` | 8080 | BFF (Thymeleaf UI), OIDC-клиент Keycloak, проксирование вызовов в backend с прокидыванием JWT и `X-Correlation-Id`, Bucket4j rate-limit, Resilience4j CircuitBreaker. |
| `backend-service` | 8081 | REST API (`/api/models`, `/api/datasets`, `/api/tasks`, `/api/me/notifications`). Resource server, валидация JWT по JWKS Keycloak. JPA + Liquibase в Postgres, заливка артефактов в MinIO, продюсер/консьюмер Kafka. |
| `inference-worker` | 8082 | Слушает `inference.tasks`. Скачивает модель + датасет из MinIO, открывает `OrtSession`, гонит инференс. Поддержка classification (raw float32) и YOLO (image input, NMS, detect/pose, e2e). Считает recall + PCK@5px. Публикует прогресс/итог в `inference.status`. Retry + DLQ через Resilience4j. |
| `notification-service` | 8083 | Слушает `inference.notifications` — обогащённые события от backend (с email/chat_id). Шлёт SMTP и Telegram. |

## Поток постановки и обработки задачи

```mermaid
sequenceDiagram
    actor User as Пользователь
    participant GW as gateway
    participant BE as backend
    participant DB as Postgres
    participant MQ as Kafka
    participant W as worker
    participant S3 as MinIO
    participant NS as notif-service

    User->>GW: POST /tasks (modelId, datasetId) + JWT-cookie
    GW->>BE: POST /api/tasks (Bearer JWT)
    BE->>DB: INSERT inference_task (status=PENDING)
    BE->>MQ: publish inference.tasks
    BE-->>GW: 201 Created
    GW-->>User: redirect /tasks

    MQ->>W: consume inference.tasks
    W->>S3: GET model + dataset
    W->>MQ: inference.status (RUNNING 15%)
    MQ->>BE: consume inference.status
    BE->>DB: UPDATE inference_task

    loop по сэмплам
        W->>W: OrtSession.run(...)
        W->>MQ: inference.status (progress)
    end

    W->>S3: PUT results.csv
    W->>MQ: inference.status (SUCCEEDED, accuracy)
    MQ->>BE: consume inference.status
    BE->>DB: status=SUCCEEDED, accuracy, result_s3_key
    BE->>MQ: publish inference.notifications (enriched: email, tg)
    MQ->>NS: consume inference.notifications
    NS->>NS: SMTP send / Telegram send

    User->>GW: GET /tasks (HTMX каждые 3с)
    GW->>BE: GET /api/tasks
    BE-->>GW: tasks с обновлённым статусом
```

## Поток аутентификации

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant Caddy
    participant GW as gateway
    participant KC as Keycloak
    participant BE as backend

    User->>Browser: открыть /
    Browser->>Caddy: GET /
    Caddy->>GW: forward
    GW-->>Browser: страница / + "Войти"
    User->>Browser: клик "Войти"
    Browser->>GW: GET /oauth2/authorization/keycloak
    GW-->>Browser: 302 → Keycloak /auth/realms/onnxi/protocol/openid-connect/auth?...
    Browser->>Caddy: GET /auth/...
    Caddy->>KC: forward
    KC-->>Browser: форма логина
    User->>Browser: заполнить
    Browser->>KC: POST credentials
    KC-->>Browser: 302 → gateway/login/oauth2/code/keycloak?code=...
    Browser->>GW: GET /login/oauth2/code/keycloak?code=...
    GW->>KC: POST /token (code + client_secret) [через docker-сеть]
    KC-->>GW: access_token + refresh_token
    GW->>GW: создать HttpSession в Redis
    GW-->>Browser: 302 → /profile  Set-Cookie: SESSION=...
    Browser->>GW: GET /profile (cookie)
    GW->>BE: GET /api/me (Bearer access_token)
    BE->>KC: GET JWKS (кэшируется)
    KC-->>BE: открытый ключ
    BE->>BE: валидация JWT, JIT-регистрация user
    BE-->>GW: { id, email, displayName }
    GW-->>Browser: страница профиля
```

## База данных

```mermaid
erDiagram
    users ||--o{ models : owns
    users ||--o{ datasets : owns
    users ||--o{ inference_tasks : owns
    users ||--|| notification_prefs : has
    models ||--o{ inference_tasks : "used by"
    datasets ||--o{ inference_tasks : "used by"
    inference_tasks ||--o{ task_events : "audit"

    users {
        uuid id PK
        varchar kc_subject UK
        varchar email UK
        varchar display_name
        timestamptz created_at
    }
    models {
        uuid id PK
        uuid owner_id FK
        varchar name
        varchar s3_key
        bigint size_bytes
        varchar input_name
        varchar output_name
        varchar input_shape
        varchar output_shape
        timestamptz uploaded_at
    }
    datasets {
        uuid id PK
        uuid owner_id FK
        varchar name
        varchar kind "LABELED|UNLABELED"
        varchar s3_key
        bigint size_bytes
        int file_count
        timestamptz uploaded_at
    }
    inference_tasks {
        uuid id PK
        uuid owner_id FK
        uuid model_id FK
        uuid dataset_id FK
        varchar status "PENDING|RUNNING|SUCCEEDED|FAILED|CANCELED"
        int progress_pct
        text error_message
        varchar result_s3_key
        numeric accuracy
        timestamptz created_at
        timestamptz started_at
        timestamptz finished_at
    }
    notification_prefs {
        uuid user_id PK,FK
        bool email_enabled
        bool telegram_enabled
        varchar telegram_chat_id
        timestamptz updated_at
    }
    task_events {
        bigserial id PK
        uuid task_id FK
        varchar event_type
        text payload
        timestamptz occurred_at
    }
```

## Kafka топики

| Топик | Producer | Consumer | Назначение |
|---|---|---|---|
| `inference.tasks` | backend | worker | Постановка задачи на обработку. |
| `inference.status` | worker | backend | Прогресс и финальный статус. |
| `inference.notifications` | backend | notification-service | Обогащённое событие с контактами пользователя. |
| `inference.tasks.DLQ` | worker (через `DeadLetterPublishingRecoverer`) | (ручной осмотр) | Сообщения, упавшие N раз с ExponentialBackOff. |

Спецификация сообщений — в `shared-dto/` (`InferenceTaskMessage`, `InferenceStatusMessage`,
`NotificationEvent`).

## Отказоустойчивость

| Точка отказа | Защита |
|---|---|
| backend HTTP временно недоступен (gateway → backend) | Resilience4j CircuitBreaker (sliding window 20, opens at 50% failures or 80% slow @ 4s); read-методы имеют fallback с пустыми списками. |
| Kafka consumer крашится | `DefaultErrorHandler` с `ExponentialBackOff` (2s → 30s, max 2 мин) → DLQ. |
| Inference падает на сэмпле | TaskConsumer ловит исключение, публикует `FAILED` в `inference.status`, пробрасывает выше → попадает в retry. |
| MinIO недоступен | Backend отдаёт 502 через `ApiExceptionHandler.storageError`. |
| Keycloak недоступен | Spring Security кеширует JWKS; задачи продолжают обрабатываться. |
| Слишком много запросов | Bucket4j-Redis rate-limit 60/min/user → 429 Too Many Requests. |

## Observability

- **Метрики**: каждый сервис отдаёт `/actuator/prometheus`. Custom-метрики:
  - `inference_tasks_total{status}` — backend counter
  - `inference_duration_seconds{outcome}` — worker timer
  - JVM / HTTP / Kafka / R4J автоматом
- **Логи**: `logback-spring.xml` в profile `docker|prod` → JSON через
  `logstash-logback-encoder` с полями `service`, `correlationId`.
- **Trace correlation**: `CorrelationIdFilter` в gateway и backend; gateway пробрасывает
  `X-Correlation-Id` в backend через `RestClient.requestInterceptor`. В логах все 4
  сервиса показывают один и тот же id.
- **Dashboard**: `infra/grafana/dashboards/onnxi-overview.json` — 4 панели (tasks rate,
  p95 duration, JVM heap, HTTP rps по статусам).
