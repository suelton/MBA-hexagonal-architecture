package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve buscar somente os tickets do evento informado")
    public void testTicketsByEventId() throws Exception {
        // given
        final var anEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var t1 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        final var t2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), anEventId));
        ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(anEventId);

        // then
        Assertions.assertEquals(2, actualTickets.size());
        Assertions.assertTrue(actualTickets.stream().allMatch(t -> t.eventId().equals(anEventId)));
        Assertions.assertTrue(actualTickets.contains(t1));
        Assertions.assertTrue(actualTickets.contains(t2));
    }

    @Test
    @DisplayName("Deve retornar lista vazia para evento sem tickets")
    public void testTicketsByEventIdEmpty() throws Exception {
        Assertions.assertTrue(ticketRepository.ticketsByEventId(EventId.unique()).isEmpty());
    }

    @Test
    @DisplayName("Deve persistir o status CANCELLED de um ticket")
    public void testPersistCancelledTicket() throws Exception {
        // given
        final var aTicket = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), EventId.unique()));

        // when
        aTicket.cancel();
        ticketRepository.update(aTicket);

        // then
        final var actualTicket = ticketRepository.ticketOfId(aTicket.ticketId()).get();
        Assertions.assertEquals(TicketStatus.CANCELLED, actualTicket.status());
    }
}
