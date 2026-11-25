package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar todos os tickets do evento e somente eles")
    public void testCancelEventTickets() throws Exception {
        // given
        final var cancelledEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var ticketRepository = new InMemoryTicketRepository();
        final var t1 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), cancelledEventId));
        final var t2 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), cancelledEventId));
        final var other = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), otherEventId));

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(new CancelEventTicketsUseCase.Input(cancelledEventId.value()));

        // then
        Assertions.assertEquals(cancelledEventId.value(), output.eventId());
        Assertions.assertEquals(2, output.cancelledTickets());
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketOfId(t1.ticketId()).get().status());
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketOfId(t2.ticketId()).get().status());
        Assertions.assertEquals(TicketStatus.PENDING, ticketRepository.ticketOfId(other.ticketId()).get().status());
    }

    @Test
    @DisplayName("Reprocessar o cancelamento dos tickets é idempotente")
    public void testCancelEventTicketsIdempotent() throws Exception {
        // given
        final var eventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();
        final var t1 = ticketRepository.create(Ticket.newTicket(CustomerId.unique(), eventId));

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var input = new CancelEventTicketsUseCase.Input(eventId.value());
        useCase.execute(input);

        // when
        final var output = Assertions.assertDoesNotThrow(() -> useCase.execute(input));

        // then
        Assertions.assertEquals(1, output.cancelledTickets());
        Assertions.assertEquals(TicketStatus.CANCELLED, ticketRepository.ticketOfId(t1.ticketId()).get().status());
    }

    @Test
    @DisplayName("Evento sem tickets não deve falhar")
    public void testCancelEventWithoutTickets() throws Exception {
        // given
        final var ticketRepository = new InMemoryTicketRepository();

        // when
        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var output = useCase.execute(new CancelEventTicketsUseCase.Input(EventId.unique().value()));

        // then
        Assertions.assertEquals(0, output.cancelledTickets());
    }
}
