## 🧞 Commands

Команды frontend выполняются из папки `frontend`:

| Command                   | Action                                           |
| :------------------------ | :----------------------------------------------- |
| `cd frontend; npm install`             | Установить зависимости                           |
| `cd frontend; npm run dev`             | Запустить локальный сервер `localhost:4321`      |
| `cd frontend; npm run build`           | Скомпилировать ваш рабочий сайт в `frontend/dist/`      |
| `cd frontend; npm run preview`         | Предварительно просмотреть сборку локально перед развертыванием     |
| `cd frontend; npm run astro ...`       | Выполнять команды интерфейса командной строки, такие как `astro add`, `astro check` |
| `cd frontend; npm run astro -- --help` | Получить помощь по использованию Astro CLI       |

## Backend и ИИ-провайдеры

Backend (Spring Boot, порт `8080`) запускается из `backend/`:

```
cd backend
mvn spring-boot:run
```

Провайдер ИИ выбирается переменной `AI_PROVIDER` (по умолчанию `mock`):

- `mock` — встроенный офлайн-провайдер, работает без ключей;
- `openai` — любой OpenAI-совместимый API (`AI_API_KEY`);
- `gigachat` — нейросеть GigaChat (Сбер), OpenAI-совместимый API.

### GigaChat (Сбер)

Подключение двухступенчатое: по Authorization key получается `access_token`
(OAuth2, живёт ~30 минут, обновляется автоматически), которым авторизуются
запросы к `https://api.giga.chat/v1/chat/completions`.

Секреты держите в `backend/.env` (в `.gitignore`) или в переменных окружения.
Пример настроек — `.env.example`:

```
AI_PROVIDER=gigachat
AI_GIGACHAT_AUTH_KEY=<Authorization Key из Studio Сбера, base64>
AI_GIGACHAT_SCOPE=GIGACHAT_API_PERS
AI_GIGACHAT_OAUTH_URL=https://ngw.devices.sberbank.ru:9443/api/v2/oauth
AI_GIGACHAT_BASE_URL=https://api.giga.chat
AI_GIGACHAT_MODEL=GigaChat-2
```

У GigaChat API участвует сертификат НУЦ Минцифры, который стандартный Java TLS
не доверяет. Для локальной разработки можно включить dev-флаг
`AI_GIGACHAT_INSECURE_SSL=true` (TrustAll) — **ни в коем случае не включайте
его в продакшене**.

Состояние провайдера — `GET /api/health`. Проверка реквизитов и логика работы
описаны в javadoc `AIConfiguration` и `GigaChatProvider`.
