package br.com.fullcycle.infrastructure.graphql;

import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureHttpGraphQlTester
@SpringBootTest
class EventResolverTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();
        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("createEvent continua funcionando com status opcional no type Event (regressão)")
    public void testCreateEventStillWorks() {
        graphQlTester.document("""
                        mutation { createEvent(input: {name: "Disney on Ice", date: "2021-01-01", totalSpots: 10, partnerId: "%s"}) { id name status } }
                        """.formatted(disney.partnerId().value()))
                .execute()
                .path("createEvent.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("createEvent.id").hasValue();
    }

    @Test
    @DisplayName("Deve cancelar um evento via mutation cancelEvent e consultar o status via eventOfId")
    public void testCancelAndQuery() {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));
        final var id = anEvent.eventId().value();

        graphQlTester.document("query { eventOfId(id: \"%s\") { id name date totalSpots status } }".formatted(id))
                .execute()
                .path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE")
                .path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(10);

        graphQlTester.document("mutation { cancelEvent(id: \"%s\") { id status } }".formatted(id))
                .execute()
                .path("cancelEvent.id").entity(String.class).isEqualTo(id)
                .path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");

        graphQlTester.document("query { eventOfId(id: \"%s\") { id status } }".formatted(id))
                .execute()
                .path("eventOfId.status").entity(String.class).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("cancelEvent em evento já cancelado deve retornar erro")
    public void testCancelTwice() {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 10, disney));
        final var mutation = "mutation { cancelEvent(id: \"%s\") { id status } }".formatted(anEvent.eventId().value());

        graphQlTester.document(mutation).execute().path("cancelEvent.status").hasValue();

        graphQlTester.document(mutation).execute()
                .errors()
                .satisfy(errors -> org.junit.jupiter.api.Assertions.assertFalse(errors.isEmpty()));
    }

    @Test
    @DisplayName("eventOfId de evento inexistente retorna null")
    public void testQueryNotFound() {
        graphQlTester.document("query { eventOfId(id: \"%s\") { id } }".formatted(EventId.unique().value()))
                .execute()
                .path("eventOfId").valueIsNull();
    }
}
