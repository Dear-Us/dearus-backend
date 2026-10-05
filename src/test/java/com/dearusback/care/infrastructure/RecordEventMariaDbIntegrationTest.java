package com.dearusback.care.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.port.in.RecordEventSubscriber;
import com.dearusback.care.application.port.in.SubscribeRecordEventsUseCase;
import com.dearusback.care.application.port.out.RecordChangePublisher;
import com.dearusback.care.application.service.RecordSubscriberRegistry;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(classes = DearUsBackApplication.class)
class RecordEventMariaDbIntegrationTest {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("dearus_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MARIADB::getJdbcUrl);
        properties.add("spring.datasource.username", MARIADB::getUsername);
        properties.add("spring.datasource.password", MARIADB::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("dearus.invitation.base-url", () -> "https://app.example.com/invite");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired RecordSubscriberRegistry registry;
    @Autowired SubscribeRecordEventsUseCase subscriptions;
    @Autowired RecordChangePublisher publisher;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void ownerAndCaregiverReceiveStartedAndCompletedThenHomeShowsCommittedState() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        long otherBaby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(otherBaby, owner, "OWNER", "ACTIVE");
        MvcResult ownerStream = connect(baby, token(owner));
        MvcResult caregiverStream = connect(baby, token(caregiver));
        MvcResult otherStream = connect(otherBaby, token(owner));
        try {
            assertThat(registry.subscriberCount(baby)).isEqualTo(2);
            assertThat(ownerStream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
            assertThat(ownerStream.getResponse().getContentAsString()).contains(":heartbeat");

            long recordId = start(baby, owner);
            assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?",
                    String.class, recordId)).isEqualTo("IN_PROGRESS");
            assertEvent(events(ownerStream), 0, "RECORD_STARTED", baby, recordId, "SLEEP");
            assertThat(ownerStream.getResponse().getContentAsString()).contains("event:record-change");
            assertEvent(events(caregiverStream), 0, "RECORD_STARTED", baby, recordId, "SLEEP");
            assertThat(events(otherStream)).isEmpty();

            complete(baby, recordId, caregiver);
            assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?",
                    String.class, recordId)).isEqualTo("COMPLETED");
            assertEvent(events(ownerStream), 1, "RECORD_COMPLETED", baby, recordId, "SLEEP");
            assertEvent(events(caregiverStream), 1, "RECORD_COMPLETED", baby, recordId, "SLEEP");
            assertThat(events(otherStream)).isEmpty();
            JsonNode home = mapper.readTree(mvc.perform(get("/api/v1/babies/{babyId}/home", baby)
                            .header("Authorization", "Bearer " + token(owner)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(home.path("data").path("activeRecord").isNull()).isTrue();
            assertThat(home.path("data").path("lastCompletedRecord").path("recordId").asLong())
                    .isEqualTo(recordId);
            assertThat(events(ownerStream)).hasSize(2);
        } finally {
            close(ownerStream);
            close(caregiverStream);
            close(otherStream);
        }
        assertThat(registry.subscriberCount(baby)).isZero();
    }

    @Test
    void eachSuccessfulCareMutationEmitsExactlyOneMinimalEvent() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        MvcResult stream = connect(baby, token(owner));
        try {
            long completed = create(baby, owner);
            long started = start(baby, owner);
            complete(baby, started, owner);
            long canceled = start(baby, owner);
            cancel(baby, canceled, owner);
            edit(baby, completed, owner);
            deleteRecord(baby, completed, owner);
            List<JsonNode> events = events(stream);
            assertThat(events).hasSize(7);
            assertEvent(events, 0, "RECORD_CREATED", baby, completed, "OTHER");
            assertEvent(events, 1, "RECORD_STARTED", baby, started, "SLEEP");
            assertEvent(events, 2, "RECORD_COMPLETED", baby, started, "SLEEP");
            assertEvent(events, 3, "RECORD_STARTED", baby, canceled, "SLEEP");
            assertEvent(events, 4, "RECORD_CANCELED", baby, canceled, "SLEEP");
            assertEvent(events, 5, "RECORD_UPDATED", baby, completed, "OTHER");
            assertEvent(events, 6, "RECORD_DELETED", baby, completed, "OTHER");
        } finally {
            close(stream);
        }
    }

    @Test
    void publisherDeliversOnlyAfterCommitAndNeverAfterRollback() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        MvcResult stream = connect(baby, token(owner));
        try {
            RecordChangedEvent event = new RecordChangedEvent(RecordChangedEvent.EventType.RECORD_CREATED,
                    baby, 9001L, CareRecordType.OTHER, Instant.now());
            TransactionTemplate template = new TransactionTemplate(transactions);
            template.executeWithoutResult(transaction -> {
                publisher.publish(event);
                assertThat(eventsUnchecked(stream)).isEmpty();
                transaction.setRollbackOnly();
            });
            assertThat(events(stream)).isEmpty();
            template.executeWithoutResult(transaction -> {
                publisher.publish(event);
                assertThat(eventsUnchecked(stream)).isEmpty();
            });
            assertEvent(events(stream), 0, "RECORD_CREATED", baby, 9001L, "OTHER");
        } finally {
            close(stream);
        }
    }

    @Test
    void failedDeliveryCannotUndoCommittedCareTransaction() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        MvcResult stream = connect(baby, token(owner));
        AtomicInteger deliveries = new AtomicInteger();
        var subscription = subscriptions.subscribe(owner, baby, new RecordEventSubscriber() {
            @Override public void send(RecordChangedEvent event) {
                deliveries.incrementAndGet();
                throw new IllegalStateException("disconnected");
            }
            @Override public void heartbeat() { }
            @Override public void close() { }
        });
        long id;
        try {
            id = create(baby, owner);
        } finally {
            subscription.close();
            close(stream);
        }
        assertThat(deliveries.get()).isEqualTo(1);
        assertEvent(events(stream), 0, "RECORD_CREATED", baby, id, "OTHER");
        assertThat(registry.subscriberCount(baby)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    @Test
    void timeoutAndErrorRemoveTheirSubscribers() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");

        MvcResult timedOut = connect(baby, token(owner));
        assertThat(registry.subscriberCount(baby)).isEqualTo(1);
        MockAsyncContext timeoutContext = (MockAsyncContext) timedOut.getRequest().getAsyncContext();
        for (AsyncListener listener : List.copyOf(timeoutContext.getListeners())) {
            listener.onTimeout(new AsyncEvent(timeoutContext));
        }
        assertThat(registry.subscriberCount(baby)).isZero();

        MvcResult errored = connect(baby, token(owner));
        assertThat(registry.subscriberCount(baby)).isEqualTo(1);
        MockAsyncContext errorContext = (MockAsyncContext) errored.getRequest().getAsyncContext();
        for (AsyncListener listener : List.copyOf(errorContext.getListeners())) {
            listener.onError(new AsyncEvent(errorContext, new IllegalStateException("connection lost")));
        }
        assertThat(registry.subscriberCount(baby)).isZero();
    }

    @Test
    void failedHeartbeatRemovesSubscriber() {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        AtomicInteger closed = new AtomicInteger();
        subscriptions.subscribe(owner, baby, new RecordEventSubscriber() {
            @Override public void send(RecordChangedEvent event) { }
            @Override public void heartbeat() { throw new IllegalStateException("connection lost"); }
            @Override public void close() { closed.incrementAndGet(); }
        });

        registry.heartbeat();

        assertThat(closed.get()).isEqualTo(1);
        assertThat(registry.subscriberCount(baby)).isZero();
    }

    @Test
    void unsuccessfulCareRequestsAndDatabaseRollbackEmitNothing() throws Exception {
        long owner = insertMember(false);
        long outsider = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        MvcResult stream = connect(baby, token(owner));
        try {
            mvc.perform(post("/api/v1/babies/{babyId}/records", baby)
                            .header("Authorization", "Bearer " + token(owner))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"UNKNOWN\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/babies/{babyId}/records/start", baby)
                            .header("Authorization", "Bearer " + token(outsider))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"SLEEP\"}"))
                    .andExpect(status().isNotFound());
            assertThat(events(stream)).isEmpty();
            long active = start(baby, owner);
            assertThat(events(stream)).hasSize(1);
            mvc.perform(post("/api/v1/babies/{babyId}/records/start", baby)
                            .header("Authorization", "Bearer " + token(owner))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"SLEEP\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/cancel", baby, active)
                            .header("Authorization", "Bearer " + token(outsider)))
                    .andExpect(status().isNotFound());
            assertThat(events(stream)).hasSize(1);
            cancel(baby, active, owner);
            assertThat(events(stream)).hasSize(2);
            mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/complete", baby, active)
                            .header("Authorization", "Bearer " + token(owner)))
                    .andExpect(status().isConflict());
            assertThat(events(stream)).hasSize(2);
            jdbc.execute("CREATE TRIGGER reject_event_detail BEFORE INSERT ON other_detail "
                    + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced rollback'");
            try {
                mvc.perform(post("/api/v1/babies/{babyId}/records", baby)
                                .header("Authorization", "Bearer " + token(owner))
                                .contentType(MediaType.APPLICATION_JSON).content(completedBody()))
                        .andExpect(status().isInternalServerError());
            } finally {
                jdbc.execute("DROP TRIGGER reject_event_detail");
            }
            assertThat(events(stream)).hasSize(2);
        } finally {
            close(stream);
        }
    }

    @Test
    void subscriptionRequiresActiveMembershipAndBearerAuthentication() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long outsider = insertMember(false);
        long left = insertMember(false);
        long removed = insertMember(false);
        long baby = insertBaby(owner, false);
        long deleted = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        MvcResult ownerStream = connect(baby, token(owner));
        MvcResult caregiverStream = connect(baby, token(caregiver));
        try {
            for (long member : List.of(outsider, left, removed)) {
                failedConnect(baby, token(member), 404);
            }
            failedConnect(deleted, token(owner), 404);
            failedConnect(Long.MAX_VALUE, token(owner), 404);
            failedConnect(0, token(owner), 400);
            failedConnect(baby, null, 401);
            failedConnect(baby, "invalid.jwt", 401);
            failedConnect(baby, token(Long.MAX_VALUE), 401);
            failedConnect(baby, token(insertMember(true)), 403);
            assertThat(registry.subscriberCount(baby)).isEqualTo(2);
        } finally {
            close(ownerStream);
            close(caregiverStream);
        }
    }

    private MvcResult connect(long babyId, String jwt) throws Exception {
        var request = get("/api/v1/babies/{babyId}/events", babyId).accept(MediaType.TEXT_EVENT_STREAM);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
        return result;
    }

    private void failedConnect(long babyId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/events", babyId).accept(MediaType.TEXT_EVENT_STREAM);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        JsonNode body = mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("data").isNull()).isTrue();
        assertThat(body.has("errorCode")).isFalse();
    }

    private void close(MvcResult stream) {
        stream.getRequest().getAsyncContext().complete();
    }

    private List<JsonNode> events(MvcResult stream) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (String line : stream.getResponse().getContentAsString().split("\\R")) {
            if (line.startsWith("data:")) {
                events.add(mapper.readTree(line.substring(5)));
            }
        }
        return events;
    }

    private List<JsonNode> eventsUnchecked(MvcResult stream) {
        try {
            return events(stream);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void assertEvent(List<JsonNode> events, int index, String type, long babyId, long recordId,
                             String recordType) {
        JsonNode event = events.get(index);
        assertThat(event.size()).isEqualTo(5);
        assertThat(event.path("eventType").asText()).isEqualTo(type);
        assertThat(event.path("babyId").asLong()).isEqualTo(babyId);
        assertThat(event.path("recordId").asLong()).isEqualTo(recordId);
        assertThat(event.path("recordType").asText()).isEqualTo(recordType);
        assertThat(event.path("occurredAt").asText()).isNotBlank();
        assertThat(OffsetDateTime.parse(event.path("occurredAt").asText()).getOffset()).isNotNull();
        assertThat(event.has("detail")).isFalse();
        assertThat(event.has("memo")).isFalse();
    }

    private long create(long babyId, long memberId) throws Exception {
        JsonNode response = mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/records", babyId)
                        .header("Authorization", "Bearer " + token(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content(completedBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return response.path("data").path("recordId").asLong();
    }

    private long start(long babyId, long memberId) throws Exception {
        JsonNode response = mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/records/start", babyId)
                        .header("Authorization", "Bearer " + token(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"SLEEP\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return response.path("data").path("recordId").asLong();
    }

    private void complete(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/complete", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId))).andExpect(status().isOk());
    }

    private void cancel(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/cancel", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId))).andExpect(status().isOk());
    }

    private void edit(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"memo\":\"수정\"}"))
                .andExpect(status().isOk());
    }

    private void deleteRecord(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(delete("/api/v1/babies/{babyId}/records/{recordId}", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId)).param("version", "1"))
                .andExpect(status().isOk());
    }

    private String completedBody() {
        return "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + Instant.now().minusSeconds(60)
                + "\",\"detail\":{\"title\":\"일상\"}}";
    }

    private long insertMember(boolean deleted) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO member "
                    + "(member_name, timezone, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"member_id"});
            statement.setString(1, "보호자");
            statement.setString(2, "Asia/Seoul");
            statement.setBoolean(3, deleted);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private long insertBaby(long creator, boolean deleted) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO baby "
                    + "(created_by_member_id, baby_name, birth_date, gender, is_deleted, created_at, updated_at, deleted_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new String[]{"baby_id"});
            statement.setLong(1, creator);
            statement.setString(2, "도윤");
            statement.setDate(3, Date.valueOf("2020-01-01"));
            statement.setString(4, "MALE");
            statement.setBoolean(5, deleted);
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            statement.setTimestamp(7, Timestamp.from(Instant.now()));
            statement.setTimestamp(8, deleted ? Timestamp.from(Instant.now()) : null);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertMembership(long babyId, long memberId, String role, String state) {
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", babyId, memberId, role, state, Timestamp.from(Instant.now()),
                "ACTIVE".equals(state) ? null : Timestamp.from(Instant.now()));
    }

    private String token(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus").subject(Long.toString(memberId))
                .issuedAt(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }
}
