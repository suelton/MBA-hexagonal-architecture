package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetEventByIdUseCaseTest {

    @Test
    @DisplayName("Deve obter um evento ativo por id")
    public void testGetById() throws Exception {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(new GetEventByIdUseCase.Input(anEvent.eventId().value())).get();

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals("Disney on Ice", output.name());
        Assertions.assertEquals("2021-01-01", output.date());
        Assertions.assertEquals(100, output.totalSpots());
        Assertions.assertEquals("ACTIVE", output.status());
    }

    @Test
    @DisplayName("Deve obter um evento cancelado por id")
    public void testGetCancelledById() throws Exception {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(new GetEventByIdUseCase.Input(anEvent.eventId().value())).get();

        // then
        Assertions.assertEquals("CANCELLED", output.status());
    }

    @Test
    @DisplayName("Deve retornar vazio quando o evento não existe")
    public void testGetByIdNotFound() throws Exception {
        // given
        final var eventRepository = new InMemoryEventRepository();

        // when
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var output = useCase.execute(new GetEventByIdUseCase.Input(EventId.unique().value()));

        // then
        Assertions.assertTrue(output.isEmpty());
    }
}
