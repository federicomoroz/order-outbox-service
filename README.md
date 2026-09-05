# order-outbox-service

[![tests](https://github.com/federicomoroz/order-outbox-service/actions/workflows/tests.yml/badge.svg)](https://github.com/federicomoroz/order-outbox-service/actions/workflows/tests.yml)

Dos microservicios Java/Spring Boot (`order-service` + `notification-service`) que resuelven un problema clásico de arquitectura de eventos: **cómo no perder un evento cuando el broker está caído justo en el momento de hacer commit de la transacción de base de datos** (el "dual write problem"). Implementa el patrón **Transactional Outbox** en el productor y **Idempotent Consumer** en el consumidor, con Kafka como broker y Postgres como base de datos (una por servicio).

Incluye un **panel de observabilidad en React** (`:8008`) que muestra el circuito completo en vivo: una orden entrando, su fila de outbox pasando de `PENDING` a `PUBLISHED`, y la notificación aterrizando en la base de datos del *otro* servicio — exactamente una vez.

## Live Demo

No aplica en esta iteración. El stack (2 Postgres + Kafka + 2 servicios + panel) no corre cómodo en el free tier de ningún proveedor cloud gratuito; ver "Cloud Deploy" más abajo. Localmente, `docker compose up --build` y **http://localhost:8008** dan la demo completa.

## Run locally

Requiere Docker Desktop.

```bash
docker compose up --build
```

Levanta, en orden de dependencias reales (no solo declaradas):

1. `postgres-orders` (5432) y `postgres-notifications` (5433) — cada uno con su propio healthcheck.
2. `kafka` (KRaft, un solo nodo, sin Zookeeper) — sano cuando `kafka-broker-api-versions` responde.
3. `kafka-init` — job de un solo uso que crea el topic `order.created.v1` y termina.
4. `order-service` (8006) y `notification-service` (8007) — arrancan recién cuando su Postgres está healthy y `kafka-init` terminó con éxito.
5. `dashboard` (8008) — el panel React servido por nginx; arranca último, cuando los dos servicios están healthy.

Los healthchecks HTTP de ambos servicios pegan a `/actuator/health`; el del dashboard, a `/`.

Con todo arriba, **abrir http://localhost:8008** — ahí se ve el circuito completo en vivo y se pueden crear órdenes sin salir del navegador.

Para correrlo sin Docker (desarrollo local): instalar JDK 21 y Maven, levantar Postgres y Kafka a mano, y ajustar `application.yml` de cada módulo (o pasar las mismas variables de entorno que usa `docker-compose.yml`). El dashboard solo necesita Node 20.19+ / 22.12+:

```bash
cd dashboard
npm install
npm run dev     # http://localhost:5173, ya contemplado en la lista de orígenes CORS
```

## User Guide

### El panel (http://localhost:8008)

La forma corta de entender el sistema es mirarlo funcionar. El panel muestra tres columnas — **Órdenes → Outbox → Notificaciones** — que son literalmente tres tablas de **dos bases de datos distintas**, y refresca cada segundo:

```
┌──────────────┐   misma       ┌──────────────┐   relay 2s →   ┌──────────────────┐
│   Órdenes    │  transacción  │    Outbox    │     Kafka      │  Notificaciones  │
│ postgres-    │ ────────────▶ │ PENDING ───▶ │ ─────────────▶ │  postgres-       │
│  orders      │               │  PUBLISHED   │                │  notifications   │
└──────────────┘               └──────────────┘                └──────────────────┘
        order-service (:8006)                          notification-service (:8007)
```

El botón **Crear orden** (o **ráfaga ×5**) hace el mismo `POST /api/orders` que el `curl` de abajo. A partir de ahí se ve, sin tocar la terminal:

- la fila aparecer en el outbox como `PENDING`, con `publishedAt` vacío;
- esa misma fila pasar a `PUBLISHED` un par de segundos después, con la latencia real `commit → ack` calculada;
- la notificación aparecer en la columna 3 — que sale de la base del **otro** servicio — exactamente una vez;
- si algún evento agotó los reintentos rápidos, la fila queda `FAILED` en rojo con su contador de intentos **y la cuenta regresiva al próximo** (`reintenta en 32s`): rojo acá significa *degradado y recuperándose solo*, no *abandonado*.

Todo lo que sigue en esta sección es el mismo circuito hecho a mano.

### A mano, con curl

Crear una orden:

```bash
curl -i -X POST http://localhost:8006/api/orders \
  -H "Content-Type: application/json" \
  -d '{
        "customerId": "11111111-1111-1111-1111-111111111111",
        "productId": "sku-demo-1",
        "quantity": 2,
        "unitPriceAmount": 19.99,
        "unitPriceCurrency": "USD"
      }'
```

Responde `201 Created` con `Location` apuntando a `GET /api/orders/{id}` — y esto pasa **sin tocar Kafka para nada**: la orden y su evento en el outbox se escriben en una sola transacción de Postgres, el publish a Kafka lo hace el relay unos segundos después, de forma asincrónica.

Ver el estado del outbox (lo mismo que consume la columna del medio del panel):

```bash
curl http://localhost:8006/api/outbox
```

O directamente contra la base, si se quiere confirmar que el endpoint no está inventando nada:

```bash
docker exec -it order-outbox-service-postgres-orders-1 \
  psql -U orders -d orders -c "SELECT aggregate_id, status, publish_attempts, next_attempt_at FROM outbox_events;"
```

Recuperar la orden creada:

```bash
curl http://localhost:8006/api/orders/<id>
```

Ver las notificaciones generadas en `notification-service` (endpoint de verificación manual):

```bash
curl http://localhost:8007/api/notifications
```

A los ~2 segundos de crear la orden (el relay pollea cada `outbox.relay.poll-interval-ms`, 2000 por defecto), el outbox pasa a `PUBLISHED` y aparece una fila nueva en `notifications`.

## Troubleshooting

- **`docker compose up` se queda esperando y `kafka` nunca pasa a healthy**: la primera vez que arranca KRaft puede tardar 20-30s en formatear su storage. `docker compose logs kafka` para ver el progreso; el healthcheck tiene `start_period: 20s` y `retries: 10` para darle margen.
- **`order-service`/`notification-service` no arrancan ("Schema-validation" en el log)**: significa que Flyway no corrió o corrió parcial. Con `ddl-auto: validate`, Hibernate nunca crea ni corrige el schema — si las migraciones no corrieron, falla rápido y ruidoso en vez de generar un schema silenciosamente distinto al versionado. `docker compose logs order-service` muestra el error exacto de Flyway.
- **El panel carga pero las tres columnas quedan vacías y los chips del header dicen "sin respuesta"**: es CORS, no los servicios. La consola del navegador lo dice explícitamente ("blocked by CORS policy"). Pasa cuando el panel se abre desde un origen que no está en `WEB_CORS_ALLOWED_ORIGINS` — por ejemplo entrando por IP de LAN (`http://192.168.x.x:8008`) en vez de `http://localhost:8008`. Un origen no autorizado recibe `403`, verificable sin navegador:
  ```bash
  curl -s -o /dev/null -w "%{http_code}\n" -H "Origin: http://otro.host" http://localhost:8006/api/outbox   # 403
  curl -s -o /dev/null -w "%{http_code}\n" -H "Origin: http://localhost:8008" http://localhost:8006/api/outbox  # 200
  ```
- **Cambié `VITE_ORDER_API_URL` y el panel sigue pegándole a `localhost`**: son variables de *build*, no de runtime. `docker compose restart dashboard` no alcanza; hace falta `docker compose up -d --build dashboard` para recompilar el bundle.
- **Una fila del outbox está en `FAILED` y en rojo en el panel**: no hay nada que hacer a mano. `FAILED` es un estado **blando**: significa "este evento agotó sus reintentos rápidos, está degradado, miralo si es viejo", no "está abandonado". El relay lo sigue levantando indefinidamente, cada vez más espaciado hasta un tope de 5 minutos, y la fila se recupera sola cuando Kafka vuelve. El panel muestra la cuenta regresiva al próximo intento justamente para que el rojo no se lea como "muerto". Si una fila lleva horas en `FAILED`, el problema no está en el outbox: es que el broker no volvió.
- **Una fila en `FAILED` tiene `next_attempt_at` viejísimo y aun así no se reintenta**: eso sí sería un bug, y apunta a que el scheduler del relay no está corriendo. `docker compose logs order-service | grep "Outbox relay run"` — si no hay líneas mientras hay filas vencidas, el `@Scheduled` no está vivo.
- **`order-service` no arranca y el log dice `outbox.relay.publish-timeout-ms ... must be strictly greater than ... delivery.timeout.ms`**: es una validación deliberada, no un accidente. Ver abajo por qué esos dos números no pueden desincronizarse.

### El caso real que originó todo esto: dos deadlines discutiendo

La primera versión de este repo tenía un bug real, reproducido durante su verificación manual con el escenario "matar Kafka a mitad de flujo" (`docker compose stop kafka`, crear una orden, `docker compose start kafka`):

- El outbox de esa orden terminó en `status=FAILED` con `publish_attempts=5`.
- La notificación correspondiente **sí existía** en `notification-service`, exactamente una vez.

Las dos cosas a la vez, porque había **dos deadlines sobre la misma operación**. `KafkaEventPublisher.publish(...)` hace `kafkaTemplate.send(...).get(publishTimeoutMs)` — un timeout **local**, de cuánto espera *este* hilo una confirmación. El cliente de Kafka, por debajo, sigue reintentando el `send()` según su propia política, `delivery.timeout.ms`, **que nunca se había configurado**: 120 segundos por default. Con el timeout local en 5s, el relay daba por perdido a los 5 segundos un `send()` que el productor terminaba entregando un minuto más tarde — sobre todo justo después de un restart del broker, mientras se reelige líder de partición (`NOT_LEADER_OR_FOLLOWER` en los logs). Consecuencias medidas: filas marcadas `FAILED` cuyos eventos **sí** se habían publicado, y el mismo evento republicado en el poll siguiente, hasta **5 copias del mismo mensaje en el topic** (confirmado reseteando el consumer group a `earliest` y reprocesando el topic entero: aparecieron 5 líneas de "Skipped duplicate event" para ese `eventId`, todas correctamente ignoradas por el consumidor idempotente).

El arreglo tiene dos mitades.

**1. Un solo deadline manda.** El del propio productor: `delivery.timeout.ms: 8000`. El `.get(...)` local pasó a ser una **red de seguridad estrictamente mayor** (`publish-timeout-ms: 10000`), no una segunda política de reintentos. Así el veredicto del productor llega siempre primero, y que salte el timeout local dejó de significar "me impacienté" para significar "el productor se colgó". La relación no queda librada a dos números sueltos en dos archivos que una edición futura puede separar: `KafkaEventPublisher` lee en el arranque el `delivery.timeout.ms` **efectivo** de la producer factory —no lo que se supone que dice el YAML— y **se niega a arrancar** si la red de seguridad no es estrictamente mayor. Un deploy mal configurado falla ruidoso en el boot en vez de resucitar el bug en silencio. `enable.idempotence` queda además explícito en `true`: ya era el default del cliente desde Kafka 3.0 (KIP-679), pero sin él los reintentos internos del productor pueden escribir copias del mismo mensaje en el topic.

**2. `FAILED` dejó de ser terminal.** Antes, a los 5 intentos la fila quedaba muerta y necesitaba intervención manual. Pero republicar es **seguro** — para eso está el consumidor idempotente — así que abandonar la fila era la peor opción disponible. Ahora un intento fallido no solo incrementa el contador: también calcula, con backoff exponencial acotado (`BASE_DELAY` 2s × `MULTIPLIER` 2 elevado a los intentos, con tope `MAX_DELAY` de 5 minutos), cuándo se puede volver a intentar. Pasados los `MAX_PUBLISH_ATTEMPTS` la fila se marca `FAILED` **y se sigue reintentando** al intervalo del tope, para siempre. `FAILED` es ahora una señal de "degradado, miralo si es viejo", no una lápida; y de paso el relay dejó de martillar cada 2 segundos a un broker que está caído.

Medido sobre el stack vivo, apagando Kafka y creando una orden (`next_attempt_at` de la propia fila, en UTC):

```
06:09:59  PENDING   intentos=0   next=—            orden creada, Kafka caído
06:10:08  PENDING   intentos=1   next=06:10:10     +2s
06:10:19  PENDING   intentos=2   next=06:10:22     +4s
06:10:32  PENDING   intentos=3   next=06:10:40     +8s
06:10:48  PENDING   intentos=4   next=06:11:04     +16s
06:11:14  FAILED    intentos=5   next=06:11:46     +32s   ← degradado, NO abandonado
06:11:15  (docker compose start kafka)
06:11:51  PUBLISHED intentos=5   next=—            se recuperó solo
```

Seis intentos en 112 segundos de outage, no cincuenta y seis. La notificación de esa orden se creó **exactamente una vez** (`06:11:51.033943Z`), sin tocar la base de datos ni reiniciar nada.

La garantía del sistema no cambió — sigue siendo **"ningún evento se pierde silenciosamente, y ningún evento produce más de un efecto"** — pero ahora el estado del outbox no le miente a quien lo mira, y ninguna fila necesita un humano para salir del pozo.

## What Problem It Solves

El "dual write problem": un servicio necesita, al procesar una request, (1) guardar un cambio en su base de datos y (2) publicar un evento a un broker de mensajes contando lo que pasó. Si estas dos escrituras son independientes, hay una ventana en la que una tiene éxito y la otra no:

```
Antes (dual write, sin garantías)
──────────────────────────────────
   HTTP request
        │
        ▼
   ┌─────────┐      ┌───────────┐
   │ guarda   │ ──▶  │ publica a │
   │ en DB    │      │  Kafka    │
   └─────────┘      └───────────┘
        │                  │
     ¿y si el proceso muere / Kafka está caído
     justo entre estos dos pasos?
        │                  │
        ▼                  ▼
   DB con el dato     Kafka SIN el evento
   (orden real)       (nadie se entera)
```

```
Después (Transactional Outbox)
──────────────────────────────────
   HTTP request
        │
        ▼
   ┌─────────────────────────────┐
   │   UNA transacción de DB:     │
   │   guarda Order + OutboxEvent │
   └─────────────────────────────┘
        │
        │  (fuera de cualquier transacción,
        │   en un scheduler aparte)
        ▼
   Relay lee outbox PENDING
   → publica a Kafka
   → marca PUBLISHED (o reintenta)

   Si Kafka está caído: el HTTP request
   ya devolvió 201 igual — el evento
   queda PENDING, no se pierde, se
   reintenta solo cuando Kafka vuelve.
```

Del lado del consumidor aparece el problema espejado: si el broker reentrega un mensaje (rebalance, restart, `at-least-once` delivery), un handler naive lo procesa dos veces. **Idempotent Consumer** lo resuelve con una tabla `processed_events` cuya *primary key* es el `eventId` del mensaje: la segunda entrega intenta el mismo `INSERT`, la constraint de la base lo rechaza, el handler la interpreta como "ya visto" y no repite el efecto.

## Architecture

```
┌──────────────────────────┐        order.created.v1        ┌────────────────────────────┐
│       order-service        │ ─────────────────────────────▶ │    notification-service      │
│         (:8006)            │            (Kafka)              │          (:8007)             │
├──────────────────────────┤                                  ├────────────────────────────┤
│ adapter/in/web              │                                  │ adapter/in/messaging          │
│  OrderController             │                                  │  OrderCreatedEventConsumer     │
│  OutboxController            │                                  │ adapter/in/web                │
│ adapter/in/scheduling        │                                  │  NotificationController        │
│  OutboxRelayScheduler         │                                  │                              │
│                              │                                  │ application/service            │
│ application/service          │                                  │  HandleOrderCreatedEventService │
│  CreateOrderService           │                                  │                              │
│  GetOrderService               │                                  │ adapter/out/persistence        │
│  OutboxRelayService            │                                  │  NotificationPersistenceAdapter │
│                              │                                  │  ProcessedEventPersistenceAdapter│
│ adapter/out/persistence       │                                  │                              │
│  OrderPersistenceAdapter       │                                  └──────────────┬───────────────┘
│  OutboxEventPersistenceAdapter │                                                 │
│ adapter/out/messaging          │                                                 ▼
│  KafkaEventPublisher            │                                  ┌────────────────────────────┐
└──────────────┬───────────────┘                                  │  postgres-notifications      │
               │                                                  │  (notifications,             │
               ▼                                                  │   processed_events)           │
┌────────────────────────────┐                                  └────────────────────────────┘
│      postgres-orders          │
│  (orders, outbox_events)       │
└────────────────────────────┘

                    ┌──────────────────────────────────────────┐
                    │        dashboard (:8008, nginx)           │
                    │   React + Vite — cliente del navegador     │
                    ├──────────────────────────────────────────┤
   GET /api/orders  │  Órdenes  →  Outbox  →  Notificaciones     │  GET /api/notifications
   GET /api/outbox  │   (col 1)    (col 2)      (col 3)          │
   ◀────────────────┤  POST /api/orders (crear tráfico)          ├────────────────▶
      :8006         │  polling cada 1s, sin estado propio        │      :8007
                    └──────────────────────────────────────────┘
```

El dashboard es un **cliente HTTP más**, no un servicio del backend: corre entero en el navegador, no habla con Postgres ni con Kafka, y no guarda nada. Consulta las dos APIs por separado a propósito — que la tercera columna venga de `:8007` y no de un proxy en `:8006` es justamente lo que demuestra que el evento cruzó el límite entre servicios.

## Full Request Lifecycle

### 1. Crear una orden (HTTP, nunca toca Kafka)

```
POST /api/orders
   │
   ▼
OrderController ──▶ CreateOrderUseCase (CreateOrderService)
   │                        │
   │                Order.place(...) valida quantity > 0
   │                        │
   │                        ▼
   │                transactionRunner.run(() -> {
   │                    orderRepository.save(order)
   │                    outboxRepository.save(outboxEvent)   ← MISMA transacción
   │                })
   │                        │
   ◀────────────────────────┘
201 Created + Location
```

### 2. Relay del outbox (scheduler, cada 2s, sin transacción abierta durante el I/O de red)

```
@Scheduled ──▶ OutboxRelayScheduler ──▶ OutboxRelayService.relayDueEvents()
                                              │
                                     findDueBatch(50, now)
                                       status ∈ {PENDING, FAILED}
                                       Y next_attempt_at IS NULL O <= now
                                              │              ↑ lo que todavía no vence, se saltea
                                  por cada evento (aislado):
                                              │
                                     eventPublisherPort.publish(event)   ← Kafka, SIN transacción
                                              │
                                   ┌──────── éxito ────────┐    ┌──── falla ────┐
                                   ▼                        │    ▼               │
                          markPublished(now)                │  recordFailedAttempt(now)
                          next_attempt_at = null            │    ++intentos, nuevo status,
                                   │                        │    next_attempt_at = now + backoff
                          transactionRunner.run(            │  transactionRunner.run(
                            () -> outboxRepository.save())  │    () -> outboxRepository.save())
                          ← transacción CORTA, por evento    │  ← PENDING, o FAILED pasados
                                                              │    MAX_PUBLISH_ATTEMPTS — pero
                                                              │    SIEMPRE con próximo intento
```

Las tres cosas que cambia un intento fallido —contador, estado y próximo intento— se calculan juntas dentro de `OutboxEvent`, nunca en el servicio ni en el SQL. Repartidas entre tres lugares terminan desincronizándose.

### 3. Consumo idempotente (Kafka listener, notification-service)

```
@KafkaListener ──▶ OrderCreatedEventConsumer.onMessage(payload)
                          │
                 deserializa JSON → HandleOrderCreatedEventCommand
                          │
                          ▼
                 HandleOrderCreatedEventUseCase.handle(command)
                          │
                 transactionRunner.run(() -> {
                     firstTime = processedEventRepository.tryMarkProcessed(...)
                     ┌──────────────┴──────────────┐
                     │ firstTime == false            │ firstTime == true
                     │ (INSERT chocó con la PK)       │
                     ▼                              ▼
                  return false                notificationRepository.save(notification)
                  (no-op, se loguea               return true
                   "skipped duplicate")
                 })
                          │
                 si algo lanzó excepción dentro de la transacción:
                 rollback completo (incluye el marcador de processed_events)
                 → el offset de Kafka NO se commitea → el broker reentrega
```

## Directory Structure

```
order-outbox-service/
├── pom.xml                          # reactor: packaging=pom, testcontainers-bom, archunit
├── docker-compose.yml                # postgres×2 + kafka (KRaft) + kafka-init + 2 servicios + dashboard
├── .github/workflows/tests.yml       # mvn -B verify en cada push/PR
│
├── order-service/
│   ├── Dockerfile                    # multi-stage: maven:3.9-eclipse-temurin-21 → jre-jammy
│   └── src/main/java/.../order/
│       ├── domain/                   # Order, Money, OutboxEvent, RetryBackoffPolicy...
│       │                             # — cero imports de Spring
│       │   └── event/                # OrderCreatedEvent (contrato de wire)
│       ├── application/
│       │   ├── port/in/              # CreateOrderUseCase, GetOrderUseCase, RelayOutboxEventsUseCase
│       │   ├── port/out/             # OrderRepository, OutboxRepository, EventSerializer,
│       │   │                         # EventPublisherPort, TransactionRunner
│       │   │                         # + OrderQueryPort, OutboxQueryPort (solo lectura, ver ISP)
│       │   └── service/              # CreateOrderService, GetOrderService, OutboxRelayService
│       ├── adapter/
│       │   ├── in/web/               # OrderController, OutboxController, GlobalExceptionHandler
│       │   ├── in/scheduling/        # OutboxRelayScheduler
│       │   ├── out/persistence/      # JPA entities + mappers + *PersistenceAdapter + SpringTransactionRunner
│       │   └── out/messaging/        # KafkaEventPublisher, JacksonEventSerializer, KafkaTopics
│       └── config/                   # OrderServiceBeanConfiguration (composition root)
│                                     # + WebCorsConfiguration (orígenes del dashboard)
│
├── notification-service/
│   ├── Dockerfile
│   └── src/main/java/.../notification/
│       ├── domain/                   # Notification, ProcessedEvent
│       ├── application/{port,service}/
│       ├── adapter/
│       │   ├── in/messaging/         # OrderCreatedEventConsumer (su propio contrato de wire)
│       │   ├── in/web/               # NotificationController (solo lectura, verificación manual)
│       │   └── out/persistence/      # incluye el INSERT ... ON CONFLICT DO NOTHING de tryMarkProcessed
│       └── config/                   # NotificationServiceBeanConfiguration + KafkaErrorHandlingConfiguration
│                                     # + WebCorsConfiguration (solo GET)
│
├── dashboard/                        # panel de observabilidad (React + Vite + TypeScript)
│   ├── Dockerfile                    # multi-stage: node:22-alpine → nginx:alpine, expone 8008
│   ├── nginx.conf                    # listen 8008, fallback SPA, cache de /assets con hash
│   ├── .env.example                  # VITE_ORDER_API_URL / VITE_NOTIFICATION_API_URL
│   └── src/
│       ├── config.ts                 # URLs por env var, intervalo de polling, topic
│       ├── api.ts                    # los 3 GET + el POST, con ApiError tipado
│       ├── types.ts                  # DTOs espejo de los records Java
│       ├── format.ts                 # horas, latencias, mediana — nada de lógica de negocio
│       ├── hooks/useCircuit.ts       # un solo setInterval → Promise.allSettled sobre los 3 endpoints
│       ├── hooks/useRowFlash.ts      # detecta qué filas cambiaron entre polls (el resaltado)
│       ├── components/               # AppHeader, MetricsBar, CreateOrderPanel, StageColumn,
│       │                             # FlowConnector, StatusPill, *Row, Legend
│       └── styles.css                # tema oscuro; el color es semántico, no decorativo
│
└── (cada módulo) src/test/java/.../architecture/HexagonalArchitectureTest.java   # 5 reglas ArchUnit
```

## Design Patterns

- **Transactional Outbox**: `Order`/`Notification` y su hecho de dominio correspondiente se escriben en una sola transacción; un proceso separado (el relay) es responsable de la entrega al broker. Elimina el dual-write problem sin necesitar 2PC ni Debezium/CDC.
- **Idempotent Consumer**: `processed_events.event_id` como *primary key* convierte "¿ya procesé esto?" en una propiedad garantizada por la base de datos, no en lógica de aplicación que puede tener carreras. Es también lo que hace *barato* reintentar del lado del productor: si republicar no cuesta nada, no hay razón para abandonar una fila del outbox nunca.
- **Exponential backoff con tope**: `RetryBackoffPolicy` es aritmética de dominio pura —sin reloj, sin scheduler, sin Spring— que recibe el instante en que falló una publicación y devuelve cuándo se puede reintentar. Crece (deja de martillar al broker caído) y deja de crecer (una fila degradada mantiene un latido lento en vez de irse a semanas vista). El tope es lo que permite que `FAILED` sea un estado blando y auto-reparable en vez de terminal.
- **Ports & Adapters (hexagonal)**: `domain/` y `application/` no importan Spring, JPA ni Hibernate — verificado por ArchUnit, no solo por convención. Los adaptadores (`adapter/in/*`, `adapter/out/*`) son el único lugar donde el framework existe.
- **Composition Root**: `OrderServiceBeanConfiguration`/`NotificationServiceBeanConfiguration` son el único punto donde las clases Java puras de `application/service` se instancian y se conectan a sus adaptadores Spring — mismo criterio que `task-queue`.
- **Repository**: `OrderRepository`, `OutboxRepository`, `NotificationRepository`, `ProcessedEventRepository` — puertos de salida con una sola implementación JPA cada uno, pero declarados como interfaz para que `application/` dependa de una abstracción, no de Hibernate.
- **CQRS-lite**: el camino de escritura pasa por casos de uso (`port/in`); las lecturas del panel van derecho por puertos de consulta separados (`OrderQueryPort`, `OutboxQueryPort`) implementados por los mismos adaptadores JPA. Separar la lectura de la escritura *a nivel de puerto* — no de base de datos ni de proceso — es lo que evita que el relay y el panel se estorben mutuamente. Ver "Decisiones puntuales".

## Decisiones puntuales

- **¿Por qué existe `TransactionRunner` en vez de `@Transactional`?** `Order` y `OutboxEvent` se escriben a través de dos puertos de salida distintos, pero tienen que caer en una sola transacción — el corazón del patrón outbox. Si esa transacción se manejara con `@Transactional` en `application/service`, esa clase pasaría a importar `org.springframework.transaction`, rompiendo la regla de que `domain`/`application` no dependen de Spring. `TransactionRunner` es un puerto (`run(Runnable)` / `run(Supplier)`) con una sola implementación, `SpringTransactionRunner`, que envuelve `TransactionTemplate` — el framework queda enteramente del lado del adaptador.
- **¿Por qué no hay un DTO compartido entre los dos servicios?** Un JAR común con el contrato del evento es el antipatrón "monolito distribuido": cualquier cambio de forma obliga a versionar y desplegar ambos servicios juntos, exactamente lo que microservicios independientes deberían evitar. Cada servicio define su propio `OrderCreatedEvent`/`OrderCreatedEventPayload`. El costo real de esto — que las dos copias puedan divergir sin que el compilador avise — se mitiga con fixtures de test espejadas en ambos módulos, no con código compartido.
- **¿Por qué dos Postgres separados?** Cada servicio es dueño de su propia base — nadie hace joins cross-servicio ni depende del schema interno del otro. Es la regla "database per service" tomada en serio, no solo declarada.
- **¿Por qué el publish a Kafka es bloqueante (`.get(timeoutMs)`)?** Porque corre en el scheduler del relay, no en el hilo de un request HTTP. No hay nadie esperando ese hilo — la simplicidad de saber inmediatamente si el publish tuvo éxito vale más que el throughput que se ganaría con un callback asincrónico acá.
- **¿Por qué `publish-timeout-ms` (10000) es mayor que `delivery.timeout.ms` (8000), y por qué el servicio no arranca si eso se rompe?** Porque son dos deadlines sobre la *misma* operación, y cuando ambos se toman en serio se contradicen: el `.get(...)` es local a este hilo y no tiene ninguna autoridad sobre el cliente de Kafka, que sigue reintentando por debajo según `delivery.timeout.ms`. Si el local vence primero, el relay declara un fracaso que el productor todavía puede convertir en éxito — que es exactamente el bug que este repo tuvo (ver Troubleshooting). La única jerarquía sana es: **el productor dicta el veredicto, el timeout local es solo una red de seguridad más grande**. Y como dos constantes en dos archivos distintos se desincronizan tarde o temprano, `KafkaEventPublisher` lee el valor *efectivo* de la producer factory al construirse y falla el arranque si la relación no se cumple. Un número mal puesto es un error de boot, no un misterio en producción tres semanas después.
- **¿Por qué `FAILED` no es terminal?** Porque el costo de reintentar es ~cero (el consumidor es idempotente) y el costo de no reintentar es una fila muerta que necesita un humano. Con esa asimetría, "dejar de intentar" no se justifica: `MAX_PUBLISH_ATTEMPTS` pasó a marcar el punto donde el evento se declara **degradado** — visible, en rojo, alertable — sin dejar de reintentarse al intervalo del tope. El `publish_attempts` sigue siendo la señal para investigar; simplemente ya no es una sentencia. Lo que sí queda fuera de alcance es una dead-letter table: con reintento perpetuo y acotado, y un demo de dos servicios, agregar un destino final sería ceremonia.
- **¿Por qué el backoff vive en `domain/` y no en el servicio del relay?** Porque un intento fallido cambia tres cosas a la vez —`publish_attempts`, `status` y `next_attempt_at`— y son un solo hecho, no tres. Calculadas en el servicio (o peor, en el `UPDATE`) se pueden aplicar a medias; dentro de `OutboxEvent.recordFailedAttempt(failedAt)` se mueven juntas o no se mueven. El adaptador solo persiste lo que el dominio ya decidió: `OutboxRepository.save()` nunca calcula un estado, y `findDueBatch(batchSize, now)` recibe el instante en vez de leer un reloj propio, para que el tiempo del relay siga siendo testeable del lado de adentro del hexágono.
- **¿Por qué `OrderQueryPort`/`OutboxQueryPort` en vez de agregar métodos a `OrderRepository`/`OutboxRepository`?** Interface Segregation aplicado en serio. `OutboxRepository` existe para el relay: `save` + `findDueBatch`, y el relay nunca quiere ver una fila `PUBLISHED` — ni siquiera una `FAILED` cuyo backoff todavía no venció. El panel quiere exactamente lo contrario: las últimas N filas *con cualquier estado*. Meter ese método en el puerto del relay obligaría a toda implementación suya — incluidos los fakes in-memory de los tests de casos de uso — a crecer un método que ese caso de uso jamás llama, y acoplaría dos consumidores que no tienen nada que ver. Son dos puertos angostos; el adaptador JPA implementa los dos (`OutboxEventPersistenceAdapter implements OutboxRepository, OutboxQueryPort`), porque lo que se segrega es lo que *ve el llamador*, no la clase que lo cumple.
- **¿Por qué los endpoints de lectura no tienen un `port/in`?** `OutboxController` y el nuevo `GET /api/orders` leen derecho a través de un puerto de salida, sin caso de uso intermedio — el mismo atajo CQRS-lite que ya usaba `NotificationController`. Son lecturas sin ninguna lógica de negocio: un `GetOutboxEventsUseCase` que solo reenvía la llamada sería ceremonia, no arquitectura. El camino de escritura (`CreateOrderUseCase`, `RelayOutboxEventsUseCase`) sí conserva sus puertos de entrada, que es donde hay reglas que proteger.
- **¿Por qué el panel usa polling y no SSE/WebSocket?** Simplicidad deliberada. Son 3 endpoints de lectura acotados y un relay que ya funciona por poll cada 2s: un `setInterval` de 1s muestra la transición `PENDING → PUBLISHED` con resolución de sobra. Un canal de streaming agregaría reconexión, backpressure y estado en el servidor a cambio de nada visible en un demo de dos servicios. Está anotado como decisión, no como omisión.
- **¿Por qué el CORS vive en `config/` y no en `application/`?** Porque es política de transporte HTTP: `domain` y `application` no saben que el mundo exterior habla HTTP, mucho menos qué orígenes de navegador están permitidos. `WebCorsConfiguration` es un adaptador de infraestructura, y la lista de orígenes es configuración (`WEB_CORS_ALLOWED_ORIGINS`), no una constante `localhost` en el código.
- **¿Por qué `tryMarkProcessed` es un solo `INSERT ... ON CONFLICT DO NOTHING` en vez de `exists()` + `insert()`?** Esa secuencia tiene una carrera real bajo entregas concurrentes del mismo mensaje. Y una variante más ingenua todavía — un `save()` de JPA normal que deje que la constraint tire una excepción — tampoco funciona bien acá: haría eso *dentro* de la misma transacción que también tiene que guardar la `Notification`, y una `ConstraintViolationException` a mitad de flush dejaría el `EntityManager` en un estado inutilizable para el resto de esa transacción. Un `INSERT` nativo con `ON CONFLICT DO NOTHING` deja que la misma constraint de base haga el trabajo sin lanzar nada, así la transacción sigue viva para decidir si escribe la notificación o no.

## SOLID

| Principio | Dónde se ve |
|---|---|
| **S**RP | Cada `application/service` tiene una sola razón para cambiar: `CreateOrderService` solo crea órdenes, `OutboxRelayService` solo relaya. `OutboxEvent` y `Order` están separados aunque ambos describan "una orden que pasó" — uno es el hecho de negocio, el otro es el mecanismo de entrega. |
| **O**CP | Agregar un nuevo tipo de evento no obliga a tocar `OutboxRelayService` — solo a agregar un nuevo caso de uso que produzca su propio `OutboxEvent`. |
| **L**SP | Cualquier implementación de `OrderRepository`/`TransactionRunner` (la real o un fake en tests) es intercambiable sin que `CreateOrderService` note la diferencia — los tests de aplicación lo prueban literalmente, corriendo contra fakes en memoria. |
| **I**SP | Puertos chicos y específicos (`ProcessedEventRepository` tiene un solo método) en vez de un `Repository` genérico gigante. El caso más explícito: los endpoints de lectura del panel no ensancharon `OrderRepository`/`OutboxRepository` — se agregaron `OrderQueryPort`/`OutboxQueryPort` aparte, para que el relay y el caso de uso de creación sigan viendo exactamente los métodos que usan. |
| **D**IP | `application/` depende de interfaces (`application/port/out/*`), nunca de las clases JPA/Kafka concretas — la dirección de la dependencia siempre apunta hacia adentro del hexágono. |
| **ArchUnit** | SOLID acá no es una convención de code review, son 5 tests que fallan el build si alguien importa `org.springframework..` desde `domain/` o `application/`, o si un adaptador esquiva los puertos e importa `application.service` directo. |

## Fuera de alcance (a propósito)

- **Debezium / CDC**: el relay pollea la tabla outbox directamente; capturar el WAL de Postgres es una optimización real para alta escala, no necesaria a este volumen.
- **Notificaciones reales (email/SMS)**: `Notification` se persiste y se puede consultar vía `GET /api/notifications` — no se envía nada de verdad.
- **Autenticación**: ningún endpoint requiere login ni token — tampoco el panel, que es de solo lectura más un `POST` de demo.
- **Streaming hacia el panel (SSE/WebSocket)**: el dashboard pollea cada 1s a propósito; ver "Decisiones puntuales".
- **Tests del dashboard**: no hay suite de front (Vitest/Testing Library). La verificación del panel es el `tsc --noEmit` del build más el checklist manual; los endpoints que consume sí están cubiertos del lado Java.
- **Paginación / filtros en los endpoints de lectura**: devuelven un tope fijo (`RECENT_ORDERS_LIMIT`, `RECENT_OUTBOX_EVENTS_LIMIT` = 50) sin cursor ni query params.
- **Deploy en la nube**: Postgres×2 + Kafka + 2 servicios + panel no entra cómodo en el free tier de ningún proveedor gratuito habitual.
- **Contract testing formal (Pact)**: la mitigación de drift entre los dos contratos de evento es manual (fixtures espejadas en tests), no automatizada con un broker de contratos.
- **Test end-to-end cross-servicio en un solo proceso JVM**: la garantía de punta a punta se valida con el checklist manual sobre `docker compose`, no en CI.
- **`SELECT ... FOR UPDATE SKIP LOCKED`**: necesario si `order-service` corriera con múltiples réplicas leyendo el mismo outbox — este demo corre una sola réplica por servicio.

## Data Model

**order-service** (`postgres-orders`)

```sql
orders (
  id                   UUID PRIMARY KEY,
  customer_id          UUID NOT NULL,
  product_id           VARCHAR(255) NOT NULL,
  quantity             INTEGER NOT NULL,
  unit_price_amount    NUMERIC(19,2) NOT NULL,
  unit_price_currency  VARCHAR(3) NOT NULL,
  status               VARCHAR(32) NOT NULL,
  created_at           TIMESTAMPTZ NOT NULL
)

outbox_events (
  id                UUID PRIMARY KEY,
  aggregate_type    VARCHAR(255) NOT NULL,
  aggregate_id      VARCHAR(255) NOT NULL,
  event_type        VARCHAR(255) NOT NULL,
  payload           TEXT NOT NULL,
  status            VARCHAR(32) NOT NULL,      -- PENDING | PUBLISHED | FAILED
  occurred_at       TIMESTAMPTZ NOT NULL,
  publish_attempts  INTEGER NOT NULL DEFAULT 0,
  published_at      TIMESTAMPTZ,
  next_attempt_at   TIMESTAMPTZ                -- NULL = "vencido ahora" (V3)
)
-- índice parcial: el relay escanea todo lo que sigue pendiente de entrega, no solo PENDING.
-- La otra mitad del filtro (next_attempt_at <= now()) no es inmutable y no puede vivir en el
-- predicado de un índice, así que queda como filtro barato sobre el conjunto ya acotado.
CREATE INDEX idx_outbox_events_due ON outbox_events (occurred_at)
    WHERE status IN ('PENDING', 'FAILED');
```

**notification-service** (`postgres-notifications`)

```sql
notifications (
  id           UUID PRIMARY KEY,
  order_id     UUID NOT NULL,
  customer_id  UUID NOT NULL,
  message      TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL
)

processed_events (
  event_id      UUID PRIMARY KEY,   -- la PK ES la garantía de idempotencia
  processed_at  TIMESTAMPTZ NOT NULL
)
```

## API Reference

### order-service (`:8006`)

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/orders` | Crea una orden. `201` + `Location`. `400` si `quantity <= 0` o falla la validación de request. |
| `GET` | `/api/orders` | Últimas órdenes, más nuevas primero, tope `RECENT_ORDERS_LIMIT` (50). Lee por `OrderQueryPort`. |
| `GET` | `/api/orders/{id}` | Devuelve la orden o `404`. |
| `GET` | `/api/outbox` | Últimos eventos del outbox, más nuevos primero, tope `RECENT_OUTBOX_EVENTS_LIMIT` (50). Lee por `OutboxQueryPort`. |
| `GET` | `/actuator/health` | Healthcheck (usado por `docker-compose.yml`). |

`GET /api/outbox` — la vista que hace visible la garantía del patrón. Sin `payload` a propósito (puede ser grande y ya viaja por Kafka):

```json
[
  {
    "id": "171ec1f8-3271-4782-80da-606afc44d382",
    "aggregateType": "Order",
    "aggregateId": "bf26afee-5d5f-4852-a846-4f8e282954c7",
    "eventType": "OrderCreated",
    "status": "PENDING",
    "publishAttempts": 0,
    "occurredAt": "2026-09-01T05:18:45.336935Z",
    "publishedAt": null,
    "nextAttemptAt": null
  }
]
```

Dos segundos después, esa misma fila: `"status": "PUBLISHED"`, `"publishedAt": "2026-09-01T05:18:47.815753Z"` y `"nextAttemptAt": null`.

`nextAttemptAt` es parte del ciclo de vida, no un detalle de implementación: es lo que permite distinguir una fila **degradada pero recuperándose sola** de una abandonada. `null` significa "ya publicada, o le toca en el próximo poll del relay"; con un valor, el relay la va a saltear hasta ese instante:

```json
{
  "status": "FAILED",
  "publishAttempts": 5,
  "publishedAt": null,
  "nextAttemptAt": "2026-09-01T05:20:19.402118Z"
}
```

Body de `POST /api/orders`:

```json
{
  "customerId": "uuid",
  "productId": "string",
  "quantity": 1,
  "unitPriceAmount": 19.99,
  "unitPriceCurrency": "USD"
}
```

### notification-service (`:8007`)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/notifications` | Lista todas las notificaciones (verificación manual + tercera columna del panel, no un caso de uso de negocio). |
| `GET` | `/actuator/health` | Healthcheck. |

### dashboard (`:8008`)

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/` | SPA React servida por nginx. Cualquier otra ruta devuelve el mismo `index.html`. |

No expone API propia: todo lo que muestra sale de los dos servicios de arriba, llamados desde el navegador.

## Configuration

Variables relevantes (con sus defaults en `application.yml`; `docker-compose.yml` las sobreescribe para apuntar a los contenedores):

| Variable | Servicio | Default | Qué controla |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | ambos | `localhost:5432/orders` (o 5433/notifications) | Conexión a Postgres |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | ambos | `localhost:9092` | Broker de Kafka |
| `outbox.relay.poll-interval-ms` | order-service | `2000` | Cada cuánto corre el scheduler del relay |
| `outbox.relay.publish-timeout-ms` | order-service | `10000` | Red de seguridad **local** de `KafkaEventPublisher`. Debe ser estrictamente mayor que `delivery.timeout.ms` o el servicio no arranca (ver Troubleshooting) |
| `spring.kafka.producer.properties.delivery.timeout.ms` | order-service | `8000` | El deadline que **manda**: cuánto reintenta el cliente de Kafka antes de fallar el `Future` |
| `spring.kafka.producer.properties.request.timeout.ms` | order-service | `4000` | Por request al broker. Kafka exige `request.timeout.ms + linger.ms <= delivery.timeout.ms` |
| `spring.kafka.producer.properties.max.block.ms` | order-service | `5000` | Cuánto bloquea `send()` esperando metadata con el broker caído |
| `spring.kafka.producer.properties.enable.idempotence` | order-service | `true` | Ya es el default del cliente (KIP-679); explícito para que un downgrade no lo apague en silencio |
| `spring.kafka.consumer.group-id` | notification-service | `notification-service` | Consumer group — usado también en el checklist de reset de offsets |
| `WEB_CORS_ALLOWED_ORIGINS` (`web.cors.allowed-origins`) | ambos | `http://localhost:8008,http://localhost:5173` | Orígenes de navegador autorizados sobre `/api/**`. 8008 es el panel en Docker; 5173 es `npm run dev`. Cualquier otro origen recibe `403`. |
| `VITE_ORDER_API_URL` | dashboard (build) | `http://localhost:8006` | A dónde apunta el navegador para `GET /api/orders`, `GET /api/outbox` y `POST /api/orders` |
| `VITE_NOTIFICATION_API_URL` | dashboard (build) | `http://localhost:8007` | A dónde apunta el navegador para `GET /api/notifications` |

Las dos `VITE_*` son variables de **build**, no de runtime: Vite las hornea en el bundle, así que `docker-compose.yml` las pasa como `build.args` (con default) y no como `environment`. Consecuencia práctica: tienen que resolver desde la máquina del visitante — `http://order-service:8006` no sirve, porque ese nombre solo existe dentro de `outbox_net`. Para exponer el panel en otro host:

```bash
VITE_ORDER_API_URL=https://api.ejemplo.com \
VITE_NOTIFICATION_API_URL=https://notif.ejemplo.com \
WEB_CORS_ALLOWED_ORIGINS=https://panel.ejemplo.com \
docker compose up --build
```

## Cloud Deploy

No aplica en esta iteración — ver "Live Demo".

## Tests

```bash
mvn -pl order-service,notification-service verify
```

Corre, para cada módulo:

- **Unit** (`*Test.java`, Surefire): dominio puro (`OrderTest`, `MoneyTest`, `OutboxEventTest`, `RetryBackoffPolicyTest`, `NotificationTest`, `ProcessedEventTest`) y casos de uso contra **fakes en memoria escritos a mano** (`InMemoryOrderRepository`, `InMemoryOutboxRepository`, `InMemoryTransactionRunner`, `AdjustableClock`, etc.) — sin Mockito. `RetryBackoffPolicyTest` fija las dos propiedades operativas del backoff: que crece y que **deja** de crecer (incluido que no desborda la aritmética con un `publishAttempts` de `Integer.MAX_VALUE`). `OutboxRelayServiceTest` prueba que una fila cuyo backoff no venció se **saltea** — el relay no la reintenta — y que una fila ya `FAILED` sigue en la rotación y se recupera sola cuando el publisher deja de fallar. El reloj se adelanta a mano (`AdjustableClock`): un backoff de cinco minutos se ejercita en microsegundos.
- **Web** (`OrderControllerTest`, `OutboxControllerTest`, también Surefire): `MockMvc` standalone sobre el controlador real, cableado a esos mismos fakes — sin `ApplicationContext` ni base de datos. Van por HTTP y no por llamada directa porque lo que más fácil se rompe es el ruteo: `GET /api/orders` (colección) tiene que convivir con `GET /api/orders/{id}` sin taparse. `OutboxControllerTest` recorre el ciclo completo de una fila: `PENDING` sin `publishedAt` → `PUBLISHED` con uno.
- **Arquitectura** (`HexagonalArchitectureTest`, también Surefire): las 5 reglas ArchUnit descritas en SOLID.
- **Integración** (`*IT.java`, Failsafe, Testcontainers — Postgres y Kafka reales):
  - `OrderPersistenceAdapterIT` — el test insignia: fuerza una excepción a mitad de una transacción y confirma que ni `Order` ni `OutboxEvent` quedaron persistidos. Cubre además los dos puertos de consulta contra el `ORDER BY` real de Postgres, incluida una fila `PUBLISHED` que el relay nunca vería, y el filtro de vencimiento del backoff contra SQL real: una fila `FAILED` es invisible **antes** de su `next_attempt_at` y vuelve a aparecer **exactamente** en él. Esa parte de la query es lógica de tres valores sobre un timestamp nullable — el tipo de cosa que un fake acierta por casualidad y Postgres no.
  - `KafkaEventPublisherIT` — publica de verdad y lo verifica con un `KafkaConsumer` plano. Su contraparte unitaria, `KafkaEventPublisherTest`, no necesita broker: construye producer factories con distintos `delivery.timeout.ms` y confirma que el publisher se niega a arrancar cuando la red de seguridad local no es estrictamente mayor (incluido el caso "nadie configuró `delivery.timeout.ms`", donde el default del cliente son 120s y arrancar sería revivir el bug).
  - `ProcessedEventPersistenceAdapterIT` — confirma que el segundo `tryMarkProcessed` con el mismo `eventId` da `false` por la constraint real de Postgres.
  - `OrderCreatedEventConsumerIT` — el test más importante del repo: produce el mismo mensaje dos veces, confirma exactamente una fila en `notifications`.

Estado verificado en esta máquina: **72 tests unitarios/web/de arquitectura, 14 tests de integración con Testcontainers — los 86 en verde**, más el checklist manual completo sobre `docker compose up` (crear orden, ver el outbox publicarse, matar Kafka a mitad de flujo y confirmar que no se pierde el evento, ver los intentos espaciarse con backoff hasta llegar a `FAILED` y **recuperarse solos** cuando Kafka vuelve, resetear offsets a `earliest` y reprocesar el topic entero sin duplicados, reiniciar `notification-service` a mitad de un burst de órdenes).

El dashboard no tiene suite propia (ver "Fuera de alcance"); su red de seguridad es `tsc --noEmit`, que corre como primer paso de `npm run build` y por lo tanto también dentro del `docker compose up --build`:

```bash
cd dashboard && npm run build
```

## Tech Stack

**Backend** — Java 21 · Spring Boot 3.3 (Web, Data JPA, Kafka, Validation, Actuator) · Maven (reactor multi-módulo) · PostgreSQL 16 · Apache Kafka (KRaft, sin Zookeeper) · Flyway · Testcontainers · ArchUnit · JUnit 5 + AssertJ

**Panel** — React 19 · TypeScript (`strict`) · Vite · CSS a mano, sin framework de UI ni librería de componentes · nginx (imagen de runtime)

**Infra** — Docker / Docker Compose · GitHub Actions
