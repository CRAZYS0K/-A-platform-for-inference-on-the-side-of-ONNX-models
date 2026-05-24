# ONNXI

Web-платформа для инференса ONNX-моделей: пользователь загружает свою модель и
датасет, ставит задачу в очередь, получает результаты с визуализацией bbox/keypoints
и метриками точности.

## Возможности

- Регистрация и вход через Keycloak (OIDC + PKCE, опционально social login).
- Загрузка `.onnx` моделей (валидация через `OrtSession`) и ZIP-датасетов
  (плоских или в YOLO-формате `images/labels/{train,val}`).
- Очередь задач на Kafka, инференс в отдельном worker через ONNX Runtime.
- Поддержка YOLO 11 / 26 (detect и pose, classic и end-to-end), классификации,
  авто-NMS и de-letterbox, метрики recall и PCK@5px.
- Просмотр результатов в браузере: SVG-оверлей с bounding boxes и keypoints,
  переключатели слоёв, скачивание labels в YOLO-формате (ZIP).
- Email-уведомления при завершении задач.
- Отмена запущенных задач, авто-обновление статуса через HTMX.

## Запуск

```bash
docker compose -f docker/docker-compose.yml up -d
```

UI откроется на <https://localhost> (Caddy выдаст self-signed сертификат, согласитесь
в браузере на первый раз).

## Структура репозитория

```
.
├── shared-dto/              # DTO для Kafka сообщений
├── gateway-service/         # Thymeleaf UI + OAuth2 client + rate-limit + CircuitBreaker
├── backend-service/         # REST API + JPA + Liquibase + Kafka producer/consumer
├── inference-worker/        # Kafka consumer + MinIO + ONNX Runtime + YOLO parser
├── notification-service/    # Kafka consumer + JavaMail
├── docker/                  # docker-compose + Caddyfile + Keycloak realm
├── infra/                   # Prometheus + Grafana provisioning
├── docs/                    # архитектура, runbook, отчёт
└── .github/workflows/       # CI: build + test + GHCR push + OWASP DC
```
