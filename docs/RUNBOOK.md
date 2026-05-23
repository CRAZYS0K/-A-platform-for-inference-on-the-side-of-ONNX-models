# Runbook: сквозной сценарий для защиты

Пошаговая инструкция продемонстрировать платформу end-to-end на реальной YOLO-pose
модели (`last(3).onnx`) и YOLO-датасете (`test_data.zip`).

## 0. Запуск стека

```bash
cd onnxi
cp docker/.env.example docker/.env
docker compose -f docker/docker-compose.yml --env-file docker/.env up -d
```

Подождать ~60 секунд, пока поднимутся Keycloak + Kafka + Postgres + ваши сервисы.

Проверка живости:

```bash
docker compose -f docker/docker-compose.yml ps
curl -k https://localhost/actuator/health
```

## 1. Регистрация пользователя

1. Открыть <https://localhost> (Firefox/Chrome). Принять self-signed сертификат
   (Caddy `local_certs`).
2. Нажать «Войти» → откроется Keycloak.
3. Кнопка «Register» → заполнить:
   - username (любой)
   - email (любой реальный — туда придёт уведомление о завершении)
   - password
4. После регистрации Keycloak редиректит обратно на gateway.
5. Открыть страницу «Профиль» — должны увидеть свой `subject` (UUID Keycloak), email,
   username.

## 2. Подготовка датасета

Ваш архив `test_data.zip` должен иметь структуру YOLO:

```
test_data.zip
├── images/
│   ├── train/   (worker проигнорирует)
│   └── val/     <- сюда worker пойдёт инференсить
│       ├── frame001.jpg
│       └── ...
└── labels/
    ├── train/
    └── val/
        ├── frame001.txt
        └── ...
```

Worker автоматически возьмёт **только `images/val/*`** и сопоставит с
`labels/val/*.txt`. Каждый label-файл в формате
`class x_c y_c w h kpt1_x kpt1_y kpt2_x kpt2_y` (нормализованные координаты 0..1).

## 3. Загрузка модели и датасета

1. Страница «Модели» → форма загрузки:
   - Название: `yolo26-pose-last3`
   - Файл: `C:\Users\sok\Desktop\Models\pose\last(3).onnx`
   - Submit
2. Должно появиться сообщение «Загружена модель» + строка в таблице с input/output
   shape (например `[1, 3, 640, 640]` → `[1, 11, 8400]`).
3. Страница «Датасеты»:
   - Название: `kj-gk-val`
   - Тип: `LABELED`
   - Файл: `test_data.zip`
   - Submit
4. Поле «Файлов» покажет количество элементов внутри ZIP (всё, не только val).

## 4. Создание задачи

1. Страница «Задачи» → выпадающие списки → выбрать модель и датасет → «Запустить».
2. В таблице появится строка со статусом `PENDING`.
3. Через ~3 секунды HTMX обновит → `RUNNING`, прогресс начнёт расти (15% → 25% → …).
4. По завершении — `SUCCEEDED`, прогресс 100%, поле `Accuracy` покажет PCK
   (обычно 0.7–0.99 для хорошей модели), время `Завершена` заполнится.

Если упало — статус `FAILED`, в `errorMessage` будет причина (несовпадение shape,
плохой ZIP, и т.п.).

## 5. Просмотр результатов

В MinIO Console (<https://localhost/minio>, `minioadmin/minioadmin`):

- Bucket `onnxi`:
  - `models/<userId>/<modelId>.onnx`
  - `datasets/<userId>/<datasetId>.zip`
  - `results/<userId>/<taskId>.csv` ← наш итоговый отчёт

CSV формата (для YOLO):

```
filename,detections
images/val/frame001.jpg,"[cls=1 conf=0.95 bbox=120.3,80.5,420.7,560.1 kpts=240.1/420.8/350.9/180.2]"
images/val/frame002.jpg,"[cls=0 conf=0.87 bbox=...]"
```

## 6. Уведомление

1. На странице «Уведомления» выставить флажок «Присылать email» (галка обычно уже стоит).
2. Telegram chat_id — опционально (надо завести бота и узнать chat_id).
3. После завершения задачи notification-service отправит письмо:
   - **Dev**: смотрим в Mailhog <http://localhost:8025>.
   - **Prod**: реальный SMTP.

## 7. Что показывать на защите

| Что | Где |
|---|---|
| Архитектура | `docs/ARCHITECTURE.md` (mermaid в GitHub отрендерится). |
| Все 4 сервиса работают | `docker compose ps`. |
| OIDC-flow Keycloak | Login → консоль браузера: Set-Cookie SESSION, Authorization: Bearer ... |
| Картина в БД | Postgres CLI: `\dt` → таблицы `users`, `models`, `datasets`, `inference_tasks`, `notification_prefs`, `task_events`. |
| Сообщения в Kafka | `docker exec -it docker-kafka-1 kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic inference.status --from-beginning --max-messages 5`. |
| Метрики | <https://localhost/actuator/prometheus> (отдаст gateway), Grafana dashboard. |
| CB состояние | <https://localhost/actuator/circuitbreakers>. |
| Rate-limit | `for i in 1..100; do curl https://localhost/models; done` → 429 после 60. |
| Логи с correlationId | `docker compose logs gateway-service backend-service | grep <correlationId>` — все 4 сервиса с одним и тем же ID. |
| CI зелёный | <https://github.com/CRAZYS0K/A-platform-for-inference-on-the-side-of-ONNX-models/actions>. |
| OWASP отчёт | `./mvnw -Psecurity-scan verify -DskipTests` → `target/dependency-check-report.html`. |

## 8. Известные грабли при демо

- **`OrtException: Loaded model is too old`** — экспортируйте через свежий ultralytics
  (`yolo export model=last.pt format=onnx opset=17`).
- **`Sample size N bytes does not match expected M`** — модель ожидает другой input,
  чем ваш датасет (например, не 640×640). Перезалейте датасет.
- **`Could not find a valid Docker environment`** при `./mvnw verify`** — Docker
  Desktop 29 несовместим с Testcontainers на нативном Windows; запускайте тесты
  через `wsl -d Ubuntu`.
- **YOLO non-e2e даёт ноль детекций** — эвристика `guessNumClasses` ошиблась.
  Решение: экспортировать модель с `nms=True` (end-to-end), тогда output формат
  `[1, N, 6+]` и эвристика не нужна.

## 9. Восстановление после демо

```bash
docker compose -f docker/docker-compose.yml --env-file docker/.env down -v
```

`-v` удалит volumes (Postgres/MinIO/Kafka/Keycloak data) — следующий старт будет с
чистого листа.
