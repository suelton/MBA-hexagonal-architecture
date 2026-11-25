# MBA Hexagonal Architecture — Cancelamento de Evento

Fork de [devfullcycle/MBA-hexagonal-architecture](https://github.com/devfullcycle/MBA-hexagonal-architecture), partindo da branch `clean-arch`, com a feature de **cancelamento de evento** implementada sobre a Arquitetura Hexagonal + Clean Architecture do curso.

## Pré-requisitos

- **JDK 17** (o Gradle Wrapper do projeto, 8.2.1, não roda sobre o JDK 21)
- Docker (apenas para subir o MySQL e executar a aplicação; os testes usam H2 em memória)

## Subindo o projeto

```bash
docker compose up -d                 # MySQL 8 em localhost:3306 (banco "events", root/root)
./gradlew :infrastructure:bootRun    # Windows: gradlew.bat :infrastructure:bootRun
```

- REST: `http://localhost:8080`
- GraphiQL: `http://localhost:8080/graphiql`

## Rodando a suíte de testes

```bash
./gradlew test                       # Windows: gradlew.bat test
```

Os testes de integração sobem o contexto Spring com o profile `test` (H2 em modo MySQL). Não é preciso ter o MySQL rodando.

## A feature

| Operação | REST | GraphQL |
|---|---|---|
| Cancelar evento | `POST /events/{id}/cancel` → `200 {id, status}` / `422` | `mutation cancelEvent(id: ID!): CancelEventResult!` |
| Consultar evento | `GET /events/{id}` → `200 {id, name, date, totalSpots, status}` / `404` | `query eventOfId(id: ID!): Event` |
| Consultar (pública) | `GET /events/{id}` com `X-Public: true` → `200 {id, status}` / `404` | — |

Regras de negócio (todas lançadas como `ValidationException`, HTTP 422 no REST):

- `Event not found`: o evento não existe.
- `Event already cancelled`: o evento já foi cancelado.
- `Event is cancelled`: tentativa de inscrição em um evento cancelado.

### Onde a cascata de cancelamento acontece

O `CancelEventUseCase` chama `event.cancel()`. O agregado `Event` muda seu estado para `CANCELLED` e registra o evento de domínio **`EventCancelled`** (`type = "event.cancelled"`). Ao persistir, o `EventDatabaseRepository` grava esse evento na tabela **`outbox`**, na mesma transação do update. O **`OutboxRelay`** publica o conteúdo na fila via `QueueGateway`. O **`ConsumerQueueGateway`** roteia pelo `type` e dispara o **`CancelEventTicketsUseCase`**, que busca os ingressos com `TicketRepository.ticketsByEventId` e chama `ticket.cancel()` em cada um. O método é idempotente, então reprocessar a mensagem é seguro. O agregado `Event` nunca toca em `Ticket`, e o comando de cancelamento não cancela ingressos de forma síncrona. O acoplamento entre os dois existe apenas através do `EventCancelled`, espelhando o fluxo `EventTicketReserved → CreateTicketForCustomerUseCase`.

### Principais arquivos

- **Domínio:** `Event` (+ `cancel()`, `status()`, bloqueio em `reserveTicket`), `EventStatus`, `EventCancelled`, `Ticket.cancel()`, `TicketStatus.CANCELLED`, `TicketRepository.ticketsByEventId`
- **Aplicação:** `event/CancelEventUseCase`, `event/GetEventByIdUseCase`, `ticket/CancelEventTicketsUseCase`
- **Infraestrutura:**
  - `EventEntity` (coluna `status`)
  - `TicketJpaRepository.findAllByEventId`
  - `TicketDatabaseRepository`
  - `ConsumerQueueGateway` (roteamento de `event.cancelled`)
  - presenters `GetEventByIdResponseEntity` / `PublicGetEventByIdResponseEntity`
  - `EventController`, `EventResolver`, `schema.gqls`, `UseCaseConfig`

### Testes adicionados

- **Domínio:** `EventTest` (cancelamento, `EventCancelled` em `allDomainEvents()`, cancelar duas vezes, reserva em evento cancelado, restore) e `TicketTest` (cancelamento idempotente).
- **Caso de uso (adapters in-memory):** `CancelEventUseCaseTest`, `GetEventByIdUseCaseTest`, `CancelEventTicketsUseCaseTest`, além de um novo caso em `SubscribeCustomerToEventUseCaseTest`.
- **Integração:** `CancelEventUseCaseIT` (status persistido + outbox) e `TicketDatabaseRepositoryIT` (busca por evento).
- **Drivers:** novos casos em `EventControllerTest` (REST e presenters) e `EventResolverTest` (GraphQL, incluindo regressão do `createEvent`).
- **Ponta a ponta assíncrono:** `CancelEventCascadeE2EIT`. O teste publica o JSON do `EventCancelled` no `ConsumerQueueGateway` e também percorre o caminho outbox + `OutboxRelay`, verificando por asserção que todos os ingressos do evento terminam `CANCELLED`.
