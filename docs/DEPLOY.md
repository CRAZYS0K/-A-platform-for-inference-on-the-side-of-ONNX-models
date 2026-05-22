# Deploy ONNXI на одиночный VPS

Минимальная инструкция для целевой конфигурации **4 vCPU / 6 GB RAM / 120 GB SSD, Ubuntu 24.04 LTS, Docker предустановлен** (например, vm.mini у российских хостингов).

## 1. Подготовка VPS

```bash
# логин под пользователем с sudo
ssh deploy@<VPS_IP>

# на свежей Ubuntu Docker уже стоит — проверьте
docker --version
docker compose version

# если нет — поставьте:
# sudo apt-get update && sudo apt-get install -y docker.io docker-compose-plugin
# sudo usermod -aG docker $USER && newgrp docker
```

Для проекта используем отдельную папку и пользователя:

```bash
sudo useradd -m -s /bin/bash -G docker onnxi
sudo -iu onnxi
git clone https://github.com/CRAZYS0K/A-platform-for-inference-on-the-side-of-ONNX-models.git onnxi
cd onnxi
```

## 2. Домен и DNS

У регистратора домена создайте две записи (или одну wildcard):

```
A   onnxi.example.com    →  <VPS_IPv4>
```

Дайте 5–30 минут на пропагацию DNS. Проверка:

```bash
dig +short onnxi.example.com   # должен вернуть IP вашего VPS
```

## 3. Секреты и переменные окружения

```bash
cp docker/.env.prod.example docker/.env.prod
nano docker/.env.prod        # заполните DOMAIN, ACME_EMAIL, пароли, SMTP, бот-токен
```

Базовая генерация надёжных паролей:

```bash
openssl rand -base64 24      # сгенерирует ~32-символьный пароль
```

Минимальный набор переменных, которые точно нужно поменять:
- `DOMAIN`, `ACME_EMAIL`
- `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `GRAFANA_ADMIN_PASSWORD`
- `KEYCLOAK_CLIENT_SECRET` (см. шаг 5 ниже)

## 4. Подгрузить образы из GHCR

GitHub Actions автоматически собирает образы при пуше в `main` и публикует их под
`ghcr.io/<owner>/onnxi-{gateway,backend,worker,notification}:latest`. Если репозиторий
публичный — образы тоже публичные и `docker login` не требуется. Если приватный,
залогиньтесь в GHCR с personal access token (scope `read:packages`):

```bash
echo "<GH_TOKEN>" | docker login ghcr.io -u <github-username> --password-stdin
```

## 5. Перед первым стартом — клиентский секрет Keycloak

Файл `docker/keycloak/realm-export.json` импортируется при первом старте Keycloak и создаёт
realm `onnxi` + клиент `onnxi-gateway`. Дефолтный `secret = change-me-gateway-secret`
не годится для прода — после первого запуска войдите в Keycloak admin
(`https://<domain>/auth`, логин/пароль из `.env.prod`), откройте Clients → `onnxi-gateway`
→ Credentials, скопируйте новый секрет и впишите его в `KEYCLOAK_CLIENT_SECRET` в `.env.prod`,
затем `docker compose ... up -d gateway-service` чтобы перезапустить gateway.

## 6. Первый запуск

```bash
cd docker
# подтянуть образы
docker compose -f docker-compose.prod.yml --env-file .env.prod pull

# базовый стек (без Prometheus/Grafana)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d

# или вместе с observability
docker compose -f docker-compose.prod.yml --env-file .env.prod --profile observability up -d
```

Caddy запросит сертификат у Let's Encrypt при первом обращении к домену (HTTP-01 challenge).
Проверка:

```bash
curl -I https://onnxi.example.com
# должен прийти 200/302 с заголовком strict-transport-security
```

## 7. Текущее использование памяти

С `docker compose ... up -d --profile observability` контейнеры заняты примерно так
(значения из лимитов в `docker-compose.prod.yml`):

| Сервис | mem limit | факт. RSS |
|---|---|---|
| Keycloak | 1 G | ~700 MB |
| Kafka | 768 M | ~600 MB |
| Postgres | 512 M | ~150 MB |
| gateway / backend | 512 M каждый | ~350 MB |
| inference-worker | 1300 M | ~600 MB idle / ~1.1 GB при инференсе YOLO |
| notification | 320 M | ~200 MB |
| MinIO / Redis | 256 M / 128 M | ~80 MB / ~30 MB |
| Prometheus / Grafana | 320 M / 192 M | ~150 MB / ~80 MB |
| Caddy | 96 M | ~30 MB |

Итого ~5 GB. При 6 GB RAM остаётся ~1 GB буфера на пики и кеш ОС.

## 8. Логи и мониторинг

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f gateway-service
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
```

В docker-профиле сервисы пишут структурированные JSON-логи (`logstash-encoder`) с
полями `service`, `correlationId`. Если поставите Loki/Promtail, парсить их легко.

Grafana доступна на `https://<domain>/grafana/` (логин/пароль из `.env.prod`). Дашборд
**ONNXI Overview** провижится автоматически.

## 9. Обновление до новой версии

```bash
cd ~/onnxi
git pull origin main
cd docker
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --remove-orphans
```

Простой каждого сервиса — около 10–20 секунд. Caddy продолжит обслуживать запросы,
gateway/backend по очереди перезапустятся.

## 10. Откат

```bash
# найти прошлый тэг (sha-XXXXXXX)
docker images | grep onnxi
# перепрописать в .env.prod:
# IMAGE_TAG=sha-<short-sha>
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
```

## 11. Бэкап

Volume'ы под `/var/lib/docker/volumes/onnxi_*` содержат данные. Минимально:

```bash
# на хосте, под пользователем onnxi
mkdir -p ~/backup/$(date +%F)
docker run --rm -v onnxi_postgres-data:/data -v ~/backup/$(date +%F):/out alpine \
    tar czf /out/postgres.tar.gz -C /data .
docker run --rm -v onnxi_minio-data:/data -v ~/backup/$(date +%F):/out alpine \
    tar czf /out/minio.tar.gz -C /data .
docker run --rm -v onnxi_keycloak-data:/data -v ~/backup/$(date +%F):/out alpine \
    tar czf /out/keycloak.tar.gz -C /data .
```

Скиньте архивы на S3/внешний сервер по `rsync`/`rclone` cron'ом.

## 12. Известные грабли

- **`Caddy: too many failed authorizations`** — DNS ещё не пропагировался. Подождите.
- **`502 from gateway after Keycloak restart`** — gateway кешировал старый JWKS. Просто
  подождите ~30s или `docker compose restart gateway-service`.
- **OOM при инференсе** — увеличьте `JAVA_OPTS=-Xmx512m` обратно или возьмите тариф 8 GB.
- **ONNX Runtime native-память** — не учитывается JVM heap. Lim для worker'а — 1.3 GB,
  это сумма heap + native. Если падает на больших моделях, поднимите `deploy.resources.limits.memory`.
