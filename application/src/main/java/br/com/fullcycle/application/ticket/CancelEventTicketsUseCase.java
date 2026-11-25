package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.UseCase;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.TicketRepository;

import java.util.Objects;

/**
 * Reage ao evento de domínio EventCancelled (via outbox + fila) e cancela todos os
 * ingressos do evento. É idempotente: reprocessar a mesma mensagem não quebra o fluxo,
 * pois Ticket.cancel() em um ingresso já cancelado não faz nada.
 */
public class CancelEventTicketsUseCase
        extends UseCase<CancelEventTicketsUseCase.Input, CancelEventTicketsUseCase.Output> {

    private final TicketRepository ticketRepository;

    public CancelEventTicketsUseCase(final TicketRepository ticketRepository) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository);
    }

    @Override
    public Output execute(final Input input) {
        final var tickets = ticketRepository.ticketsByEventId(EventId.with(input.eventId()));

        tickets.forEach(aTicket -> {
            aTicket.cancel();
            ticketRepository.update(aTicket);
        });

        return new Output(input.eventId(), tickets.size());
    }

    public record Input(String eventId) {
    }

    public record Output(String eventId, int cancelledTickets) {
    }
}
