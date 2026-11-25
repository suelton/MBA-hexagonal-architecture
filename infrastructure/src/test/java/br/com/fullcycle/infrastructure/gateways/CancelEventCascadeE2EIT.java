package br.com.fullcycle.infrastructure.gateways;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.application.event.CancelEventUseCase;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Prova o fluxo assíncrono de ponta a ponta: o EventCancelled trafega pelo caminho real
 * (JSON publicado no ConsumerQueueGateway, ou outbox + OutboxRelay) e o roteamento por type
 * dispara o CancelEventTicketsUseCase, deixando todos os tickets do evento CANCELLED.
 */
class CancelEventCascadeE2EIT extends IntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private QueueGateway queueGateway;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CancelEventUseCase cancelEventUseCase;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        partnerRepository.deleteAll();
    }

    @Test
    @DisplayName("JSON do EventCancelled publicado no ConsumerQueueGateway deve cancelar todos os tickets do evento")
    public void testCascadeThroughConsumerQueueGateway() throws Exception {
        // given
        final var anEventId = EventId.unique();
        final var otherEventId = EventId.unique();
        final var t1 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var t2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var other = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        final var content = mapper.writeValueAsString(new EventCancelled(anEventId));

        // when
        queueGateway.publish(content);

        // then
        awaitUntil(() -> allTicketsCancelled(anEventId));
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketOfId(t1.ticketId()).get().status());
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketOfId(t2.ticketId()).get().status());
        Assertions.assertEquals(TicketStatus.PENDING, ticketRepository.ticketOfId(other.ticketId()).get().status());

        // reprocessar a mesma mensagem é idempotente
        Assertions.assertDoesNotThrow(() -> queueGateway.publish(content));
        awaitUntil(() -> allTicketsCancelled(anEventId));
        Assertions.assertEquals(2, ticketRepository.ticketsByEventId(anEventId).size());
    }

    @Test
    @DisplayName("Cancelar o evento grava EventCancelled na outbox e o OutboxRelay dispara a cascata")
    public void testCascadeThroughOutboxAndRelay() throws Exception {
        // given
        final var aPartner = partnerRepository.create(Partner.newPartner("Disney", "41.536.538/0001-00", "disney@gmail.com"));
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner));
        final var anEventId = anEvent.eventId();

        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));

        // when
        cancelEventUseCase.execute(new CancelEventUseCase.Input(anEventId.value()));

        // then (o relay roda a cada 2s e publica na fila)
        awaitUntil(() -> allTicketsCancelled(anEventId));
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);
        Assertions.assertEquals(3, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));
    }

    private boolean allTicketsCancelled(final EventId anEventId) {
        final var tickets = ticketRepository.ticketsByEventId(anEventId);
        return !tickets.isEmpty() && tickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED);
    }

    private static void awaitUntil(final BooleanSupplier condition) throws InterruptedException {
        final var deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        Assertions.fail("Condição não atingida em " + TIMEOUT);
    }
}
