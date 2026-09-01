# order-outbox-service

[![tests](https://github.com/federicomoroz/order-outbox-service/actions/workflows/tests.yml/badge.svg)](https://github.com/federicomoroz/order-outbox-service/actions/workflows/tests.yml)

Dos microservicios Java/Spring Boot (`order-service` + `notification-service`) que resuelven un problema clásico de arquitectura de eventos: **cómo no perder un evento cuando el broker está caído justo en el momento de hacer commit de la transacción de base de datos** (el "dual write problem"). Implementa el patrón **Transactional Outbox** en el productor y **Idempotent Consumer** en el consumidor, con Kafka como broker y Postgres como base de datos (una por servicio).

Pieza de portfolio pensada para demostrar experiencia Java/Spring Boot con rigor de arquitectura hexagonal — el resto del portfolio es Python/FastAPI.

## Live Demo

No aplica en esta iteración. El stack (2 Postgres + Kafka + 2 servicios) no corre cómodo en el free tier de ningún proveedor cloud gratuito; ver "Cloud Deploy" más abajo.

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

Los healthchecks HTTP de ambos servicios pegan a `/actuator/health`.

Para correrlo sin Docker (desarrollo local): instalar JDK 21 y Maven, levantar Postgres y Kafka a mano, y ajustar `application.yml` de cada módulo (o pasar las mismas variables de entorno que usa `docker-compose.yml`).

## User Guide

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

Ver el estado del outbox directamente (solo para entender el flujo — no es un endpoint público, es una consulta a la base):

```bash
docker exec -it order-outbox-service-postgres-orders-1 \
  psql -U orders -d orders -c "SELECT aggregate_id, status, publish_attempts FROM outbox_events;"
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
- **El outbox de una orden queda en `FAILED` después de un blip de Kafka, pero la notificación de esa orden existe igual**: esto pasó de verdad durante la verificación de este mismo repo (ver más abajo) y **no es un bug** — es una consecuencia honesta de que el relay usa *at-least-once* con reintentos acotados por tiempo, no un productor transaccional. Detalle completo abajo.

### El caso real: outbox `FAILED` con notificación igual creada

Durante la verificación manual de este proyecto se reprodujo el escenario "matar Kafka a mitad de flujo" (`docker compose stop kafka`, crear una orden, `docker compose start kafka`). El resultado observado:

- El outbox de esa orden terminó en `status=FAILED` con `publish_attempts=5` (agotó `MAX_PUBLISH_ATTEMPTS`).
- La notificación correspondiente **sí existía** en `notification-service`, exactamente una vez.

¿Por qué pueden pasar las dos cosas a la vez? `KafkaEventPublisher.publish(...)` hace `kafkaTemplate.send(...).get(publishTimeoutMs)` — un timeout **local**, de cuánto espera *este* proceso una confirmación. El cliente de Kafka, por debajo, sigue reintentando el `send()` en segundo plano según su propia política (`delivery.timeout.ms`, no `max.block.ms`) mucho más allá de esos 5 segundos, sobre todo justo después de un restart del broker mientras se reelige líder de partición (`NOT_LEADER_OR_FOLLOWER` en los logs). El resultado: el relay agotó sus 5 intentos *locales* dando cada uno por perdido antes de tiempo, pero al menos uno de esos `send()` subyacentes efectivamente llegó a Kafka más tarde — y como el mismo evento se reintenta con idéntico `eventId`, hasta pueden haber llegado **varias copias** del mismo mensaje al topic (se confirmó exactamente eso: al resetear el consumer group a `earliest` y reprocesar el topic entero, aparecieron 5 líneas de "Skipped duplicate event" para ese `eventId`, todas correctamente ignoradas).

La garantía real de este sistema no es "el estado del outbox siempre refleja la verdad del broker" — es **"ningún evento se pierde silenciosamente, y ningún evento produce más de un efecto"**. Ambas partes de esa garantía se sostuvieron: la notificación se creó y **exactamente una vez**, verificado con el topic completo reprocesado desde el offset 0. Un `outbox_events.status = FAILED` en producción debería ser una señal para investigar/reintentar manualmente — no una prueba de pérdida de datos.

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
│ adapter/in/scheduling        │                                  │ adapter/in/web                │
│  OutboxRelayScheduler         │                                  │  NotificationController        │
│                              │                                  │                              │
│ application/service          │                                  │ application/service            │
│  CreateOrderService           │                                  │  HandleOrderCreatedEventService │
│  GetOrderService               │                                  │                              │
│  OutboxRelayService            │                                  │ adapter/out/persistence        │
│                              │                                  │  NotificationPersistenceAdapter │
│ adapter/out/persistence       │                                  │  ProcessedEventPersistenceAdapter│
│  OrderPersistenceAdapter       │                                  │                              │
│  OutboxEventPersistenceAdapter │                                  └──────────────┬───────────────┘
│ adapter/out/messaging          │                                                 │
│  KafkaEventPublisher            │                                                 ▼
└──────────────┬───────────────┘                                  ┌────────────────────────────┐
               │                                                  │  postgres-notifications      │
               ▼                                                  │  (notifications,             │
┌────────────────────────────┐                                  │   processed_events)           │
│      postgres-orders          │                                  └────────────────────────────┘
│  (orders, outbox_events)       │
└────────────────────────────┘
```

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
@Scheduled ──▶ OutboxRelayScheduler ──▶ OutboxRelayService.relayPendingEvents()
                                              │
                                     findPendingBatch(50)
                                              │
                                  por cada evento (aislado):
                                              │
                                     eventPublisherPort.publish(event)   ← Kafka, SIN transacción
                                              │
                                   ┌──────── éxito ────────┐    ┌──── falla ────┐
                                   ▼                        │    ▼               │
                          markPublished(now)                │  recordFailedAttempt()
                                   │                        │    │
                          transactionRunner.run(            │  transactionRunner.run(
                            () -> outboxRepository.save())  │    () -> outboxRepository.save())
                          ← transacción CORTA, por evento    │  ← PENDING otra vez, o FAILED
                                                              │    si supera MAX_PUBLISH_ATTEMPTS
```

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
├── docker-compose.yml                # postgres×2 + kafka (KRaft) + kafka-init + ambos servicios
├── .github/workflows/tests.yml       # mvn -B verify en cada push/PR
│
├── order-service/
│   ├── Dockerfile                    # multi-stage: maven:3.9-eclipse-temurin-21 → jre-jammy
│   └── src/main/java/.../order/
│       ├── domain/                   # Order, Money, OutboxEvent... — cero imports de Spring
│       │   └── event/                # OrderCreatedEvent (contrato de wire)
│       ├── application/
│       │   ├── port/in/              # CreateOrderUseCase, GetOrderUseCase, RelayOutboxEventsUseCase
│       │   ├── port/out/             # OrderRepository, OutboxRepository, EventSerializer,
│       │   │                         # EventPublisherPort, TransactionRunner
│       │   └── service/              # CreateOrderService, GetOrderService, OutboxRelayService
│       ├── adapter/
│       │   ├── in/web/               # OrderController, GlobalExceptionHandler
│       │   ├── in/scheduling/        # OutboxRelayScheduler
│       │   ├── out/persistence/      # JPA entities + mappers + *PersistenceAdapter + SpringTransactionRunner
│       │   └── out/messaging/        # KafkaEventPublisher, JacksonEventSerializer, KafkaTopics
│       └── config/                   # OrderServiceBeanConfiguration (composition root)
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
│
└── (cada módulo) src/test/java/.../architecture/HexagonalArchitectureTest.java   # 5 reglas ArchUnit
```

## Design Patterns

- **Transactional Outbox**: `Order`/`Notification` y su hecho de dominio correspondiente se escriben en una sola transacción; un proceso separado (el relay) es responsable de la entrega al broker. Elimina el dual-write problem sin necesitar 2PC ni Debezium/CDC.
- **Idempotent Consumer**: `processed_events.event_id` como *primary key* convierte "¿ya procesé esto?" en una propiedad garantizada por la base de datos, no en lógica de aplicación que puede tener carreras.
- **Ports & Adapters (hexagonal)**: `domain/` y `application/` no importan Spring, JPA ni Hibernate — verificado por ArchUnit, no solo por convención. Los adaptadores (`adapter/in/*`, `adapter/out/*`) son el único lugar donde el framework existe.
- **Composition Root**: `OrderServiceBeanConfiguration`/`NotificationServiceBeanConfiguration` son el único punto donde las clases Java puras de `application/service` se instancian y se conectan a sus adaptadores Spring — mismo criterio que `task-queue`.
- **Repository**: `OrderRepository`, `OutboxRepository`, `NotificationRepository`, `ProcessedEventRepository` — puertos de salida con una sola implementación JPA cada uno, pero declarados como interfaz para que `application/` dependa de una abstracción, no de Hibernate.

## Decisiones puntuales

- **¿Por qué existe `TransactionRunner` en vez de `@Transactional`?** `Order` y `OutboxEvent` se escriben a través de dos puertos de salida distintos, pero tienen que caer en una sola transacción — el corazón del patrón outbox. Si esa transacción se manejara con `@Transactional` en `application/service`, esa clase pasaría a importar `org.springframework.transaction`, rompiendo la regla de que `domain`/`application` no dependen de Spring. `TransactionRunner` es un puerto (`run(Runnable)` / `run(Supplier)`) con una sola implementación, `SpringTransactionRunner`, que envuelve `TransactionTemplate` — el framework queda enteramente del lado del adaptador.
- **¿Por qué no hay un DTO compartido entre los dos servicios?** Un JAR común con el contrato del evento es el antipatrón "monolito distribuido": cualquier cambio de forma obliga a versionar y desplegar ambos servicios juntos, exactamente lo que microservicios independientes deberían evitar. Cada servicio define su propio `OrderCreatedEvent`/`OrderCreatedEventPayload`. El costo real de esto — que las dos copias puedan divergir sin que el compilador avise — se mitiga con fixtures de test espejadas en ambos módulos, no con código compartido.
- **¿Por qué dos Postgres separados?** Cada servicio es dueño de su propia base — nadie hace joins cross-servicio ni depende del schema interno del otro. Es la regla "database per service" tomada en serio, no solo declarada.
- **¿Por qué el publish a Kafka es bloqueante (`.get(timeoutMs)`)?** Porque corre en el scheduler del relay, no en el hilo de un request HTTP. No hay nadie esperando ese hilo — la simplicidad de saber inmediatamente si el publish tuvo éxito vale más que el throughput que se ganaría con un callback asincrónico acá.
- **¿Por qué `tryMarkProcessed` es un solo `INSERT ... ON CONFLICT DO NOTHING` en vez de `exists()` + `insert()`?** Esa secuencia tiene una carrera real bajo entregas concurrentes del mismo mensaje. Y una variante más ingenua todavía — un `save()` de JPA normal que deje que la constraint tire una excepción — tampoco funciona bien acá: haría eso *dentro* de la misma transacción que también tiene que guardar la `Notification`, y una `ConstraintViolationException` a mitad de flush dejaría el `EntityManager` en un estado inutilizable para el resto de esa transacción. Un `INSERT` nativo con `ON CONFLICT DO NOTHING` deja que la misma constraint de base haga el trabajo sin lanzar nada, así la transacción sigue viva para decidir si escribe la notificación o no.

## SOLID

| Principio | Dónde se ve |
|---|---|
| **S**RP | Cada `application/service` tiene una sola razón para cambiar: `CreateOrderService` solo crea órdenes, `OutboxRelayService` solo relaya. `OutboxEvent` y `Order` están separados aunque ambos describan "una orden que pasó" — uno es el hecho de negocio, el otro es el mecanismo de entrega. |
| **O**CP | Agregar un nuevo tipo de evento no obliga a tocar `OutboxRelayService` — solo a agregar un nuevo caso de uso que produzca su propio `OutboxEvent`. |
| **L**SP | Cualquier implementación de `OrderRepository`/`TransactionRunner` (la real o un fake en tests) es intercambiable sin que `CreateOrderService` note la diferencia — los tests de aplicación lo prueban literalmente, corriendo contra fakes en memoria. |
| **I**SP | Puertos chicos y específicos (`ProcessedEventRepository` tiene un solo método) en vez de un `Repository` genérico gigante. |
| **D**IP | `application/` depende de interfaces (`application/port/out/*`), nunca de las clases JPA/Kafka concretas — la dirección de la dependencia siempre apunta hacia adentro del hexágono. |
| **ArchUnit** | El diferenciador real frente al resto del portfolio: SOLID acá no es una convención de code review, son 5 tests que fallan el build si alguien importa `org.springframework..` desde `domain/` o `application/`, o si un adaptador esquiva los puertos e importa `application.service` directo. |

## Fuera de alcance (a propósito)

- **Debezium / CDC**: el relay pollea la tabla outbox directamente; capturar el WAL de Postgres es una optimización real para alta escala, no necesaria para demostrar el patrón.
- **Notificaciones reales (email/SMS)**: `Notification` se persiste y se puede consultar vía `GET /api/notifications` — no se envía nada de verdad.
- **Autenticación**: ningún endpoint requiere login ni token.
- **Dashboard React**: pasada futura, no incluida acá.
- **Deploy en la nube**: Postgres×2 + Kafka + 2 servicios no entra cómodo en el free tier de ningún proveedor gratuito habitual.
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
  published_at      TIMESTAMPTZ
)
-- índice parcial: el relay solo escanea PENDING
CREATE INDEX idx_outbox_events_pending ON outbox_events (occurred_at) WHERE status = 'PENDING';
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
| `GET` | `/api/orders/{id}` | Devuelve la orden o `404`. |
| `GET` | `/actuator/health` | Healthcheck (usado por `docker-compose.yml`). |

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
| `GET` | `/api/notifications` | Lista todas las notificaciones (verificación manual, no un caso de uso de negocio). |
| `GET` | `/actuator/health` | Healthcheck. |

## Configuration

Variables relevantes (con sus defaults en `application.yml`; `docker-compose.yml` las sobreescribe para apuntar a los contenedores):

| Variable | Servicio | Default | Qué controla |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | ambos | `localhost:5432/orders` (o 5433/notifications) | Conexión a Postgres |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | ambos | `localhost:9092` | Broker de Kafka |
| `outbox.relay.poll-interval-ms` | order-service | `2000` | Cada cuánto corre el scheduler del relay |
| `outbox.relay.publish-timeout-ms` | order-service | `5000` | Cuánto espera `KafkaEventPublisher` una confirmación local (ver Troubleshooting) |
| `spring.kafka.consumer.group-id` | notification-service | `notification-service` | Consumer group — usado también en el checklist de reset de offsets |

## Cloud Deploy

No aplica en esta iteración — ver "Live Demo".

## Tests

```bash
mvn -pl order-service,notification-service verify
```

Corre, para cada módulo:

- **Unit** (`*Test.java`, Surefire): dominio puro (`OrderTest`, `MoneyTest`, `OutboxEventTest`, `NotificationTest`, `ProcessedEventTest`) y casos de uso contra **fakes en memoria escritos a mano** (`InMemoryOrderRepository`, `InMemoryOutboxRepository`, `InMemoryTransactionRunner`, etc.) — sin Mockito.
- **Arquitectura** (`HexagonalArchitectureTest`, también Surefire): las 5 reglas ArchUnit descritas en SOLID.
- **Integración** (`*IT.java`, Failsafe, Testcontainers — Postgres y Kafka reales):
  - `OrderPersistenceAdapterIT` — el test insignia: fuerza una excepción a mitad de una transacción y confirma que ni `Order` ni `OutboxEvent` quedaron persistidos.
  - `KafkaEventPublisherIT` — publica de verdad y lo verifica con un `KafkaConsumer` plano.
  - `ProcessedEventPersistenceAdapterIT` — confirma que el segundo `tryMarkProcessed` con el mismo `eventId` da `false` por la constraint real de Postgres.
  - `OrderCreatedEventConsumerIT` — el test más importante del repo: produce el mismo mensaje dos veces, confirma exactamente una fila en `notifications`.

Estado verificado en esta máquina: **43 tests unitarios + de arquitectura, 7 tests de integración con Testcontainers — los 50 en verde**, más el checklist manual completo sobre `docker compose up` (crear orden, ver el outbox publicarse, matar Kafka a mitad de flujo y confirmar que no se pierde el evento, resetear offsets a `earliest` y reprocesar el topic entero sin duplicados, reiniciar `notification-service` a mitad de un burst de órdenes).

## Tech Stack

Java 21 · Spring Boot 3.3 (Web, Data JPA, Kafka, Validation, Actuator) · Maven (reactor multi-módulo) · PostgreSQL 16 · Apache Kafka (KRaft, sin Zookeeper) · Flyway · Testcontainers · ArchUnit · JUnit 5 + AssertJ · Docker / Docker Compose · GitHub Actions
