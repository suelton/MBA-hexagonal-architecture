package br.com.fullcycle.infrastructure.rest;

import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.application.event.CreateEventUseCase;
import br.com.fullcycle.infrastructure.dtos.NewEventDTO;
import br.com.fullcycle.infrastructure.dtos.SubscribeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class EventControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private EventRepository eventRepository;

    private Customer johnDoe;
    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();

        johnDoe = customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-00", "john@gmail.com"));
        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve criar um evento")
    public void testCreate() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var result = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var actualResponse = mapper.readValue(result, NewEventDTO.class);
        Assertions.assertEquals(event.date(), actualResponse.date());
        Assertions.assertEquals(event.totalSpots(), actualResponse.totalSpots());
        Assertions.assertEquals(event.name(), actualResponse.name());
    }

    @Test
    @Transactional
    @DisplayName("Deve comprar um ticket de um evento")
    public void testReserveTicket() throws Exception {

        var event = new NewEventDTO("Disney on Ice", "2021-01-01", 100, disney.partnerId().value());

        final var createResult = this.mvc.perform(
                        MockMvcRequestBuilders.post("/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(event))
                )
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").isString())
                .andReturn().getResponse().getContentAsByteArray();

        var eventId = mapper.readValue(createResult, CreateEventUseCase.Output.class).id();

        var sub = new SubscribeDTO(johnDoe.customerId().value(), null);

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/subscribe", eventId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(sub))
                )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        var actualEvent = eventRepository.eventOfId(EventId.with(eventId)).get();
        Assertions.assertEquals(1, actualEvent.allTickets().size());
    }

    @Test
    @DisplayName("Deve cancelar um evento")
    public void testCancel() throws Exception {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var id = anEvent.eventId().value();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));

        Assertions.assertEquals(EventStatus.CANCELLED, eventRepository.eventOfId(anEvent.eventId()).get().status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelTwice() throws Exception {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var id = anEvent.eventId().value();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", id))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", id))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event already cancelled"));
    }

    @Test
    @DisplayName("Não deve cancelar um evento inexistente")
    public void testCancelNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", EventId.unique().value()))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event not found"));
    }

    @Test
    @DisplayName("Não deve inscrever cliente em evento cancelado")
    public void testSubscribeOnCancelledEvent() throws Exception {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var id = anEvent.eventId().value();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", id))
                .andExpect(MockMvcResultMatchers.status().isOk());

        var sub = new SubscribeDTO(johnDoe.customerId().value(), null);

        this.mvc.perform(
                        MockMvcRequestBuilders.post("/events/{id}/subscribe", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(sub))
                )
                .andExpect(MockMvcResultMatchers.status().isUnprocessableEntity())
                .andExpect(MockMvcResultMatchers.content().string("Event is cancelled"));
    }

    @Test
    @DisplayName("Deve obter um evento por id (representação completa)")
    public void testGet() throws Exception {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var id = anEvent.eventId().value();

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Disney on Ice"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.date").value("2021-01-01"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalSpots").value(100))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Deve obter um evento por id (representação pública com X-Public)")
    public void testGetPublic() throws Exception {
        final var anEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, disney));
        final var id = anEvent.eventId().value();

        this.mvc.perform(MockMvcRequestBuilders.post("/events/{id}/cancel", id))
                .andExpect(MockMvcResultMatchers.status().isOk());

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", id).header("X-Public", "true"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalSpots").doesNotExist());
    }

    @Test
    @DisplayName("Deve retornar 404 sem corpo para evento inexistente (ambos os presenters)")
    public void testGetNotFound() throws Exception {
        final var id = EventId.unique().value();

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", id))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));

        this.mvc.perform(MockMvcRequestBuilders.get("/events/{id}", id).header("X-Public", "true"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(""));
    }
}
