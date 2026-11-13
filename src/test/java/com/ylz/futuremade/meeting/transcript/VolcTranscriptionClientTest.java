package com.ylz.futuremade.meeting.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VolcTranscriptionClientTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void submitsSpeakerOptionsAndMapsResult() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        server.createContext("/api/v3/auc/bigmodel/submit", exchange -> {
            submitted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Api-Request-Id"));
            exchange.getResponseHeaders().add("X-Api-Status-Code", "20000000");
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.createContext("/api/v3/auc/bigmodel/query", exchange -> {
            exchange.getResponseHeaders().add("X-Api-Status-Code", "20000000");
            byte[] body = "{\"result\":{\"utterances\":[{\"start_time\":100,\"end_time\":800,\"text\":\"你好\",\"additions\":{\"speaker_id\":\"1\"}}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        VolcTranscriptionClient client = new VolcTranscriptionClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key", "volc.seedasr.auc");
        UUID id = UUID.randomUUID();
        client.submit(id, URI.create("https://example.com/sample.wav"), "wav");
        assertEquals(id.toString(), requestId.get());
        assertTrue(submitted.get().contains("\"ssd_version\":\"300\""));
        var result = client.query(id);
        assertEquals(VolcTranscriptionClient.State.SUCCEEDED, result.state());
        assertTrue(result.rawResponse().contains("\"speaker_id\":\"1\""));
        assertEquals("1", result.segments().get(0).speakerLabel());
        assertEquals("你好", result.segments().get(0).text());
    }

    @Test void reportsProcessingWithoutInventingResults() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/auc/bigmodel/query", exchange -> {
            exchange.getResponseHeaders().add("X-Api-Status-Code", "20000001");
            exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        server.start();
        var client = new VolcTranscriptionClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key", "volc.seedasr.auc");
        assertEquals(VolcTranscriptionClient.State.PROCESSING, client.query(UUID.randomUUID()).state());
        assertThrows(IllegalArgumentException.class, () -> client.submit(UUID.randomUUID(), URI.create("file:///meeting.wav"), "wav"));
    }

    @Test void supportsSpeakerAndSpeakerIdAndMissingLabels() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/auc/bigmodel/query", exchange -> {
            exchange.getResponseHeaders().add("X-Api-Status-Code", "20000000");
            byte[] body = ("{\"result\":{\"utterances\":[" +
                    "{\"start_time\":0,\"end_time\":100,\"text\":\"甲\",\"additions\":{\"speaker\":\"2\",\"speaker_id\":\"-1\"}}," +
                    "{\"start_time\":100,\"end_time\":200,\"text\":\"乙\",\"additions\":{\"speaker_id\":\"3\"}}," +
                    "{\"start_time\":200,\"end_time\":300,\"text\":\"丙\",\"additions\":{\"speaker\":\"-1\"}}]}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var client = new VolcTranscriptionClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key", "resource");
        var segments = client.query(UUID.randomUUID()).segments();
        assertEquals("2", segments.get(0).speakerLabel());
        assertEquals("3", segments.get(1).speakerLabel());
        assertEquals("unknown", segments.get(2).speakerLabel());
    }

    @Test void bindsConfiguredOptionsAndIncludesThemInRequest() throws Exception {
        var source = new MapConfigurationPropertySource(Map.of(
                "meetflow.speech.uid", "meeting-service", "meetflow.speech.speaker-version", "200"));
        SpeechRequestProperties options = new Binder(source)
                .bind("meetflow.speech", SpeechRequestProperties.class).get();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> submitted = new AtomicReference<>();
        server.createContext("/api/v3/auc/bigmodel/submit", exchange -> {
            submitted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("X-Api-Status-Code", "20000000");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        var mapper = new ObjectMapper();
        var client = new VolcTranscriptionClient(HttpClient.newHttpClient(), mapper,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-key", "resource", options);
        client.submit(UUID.randomUUID(), URI.create("https://example.com/sample.wav"), "wav");
        var body = mapper.readTree(submitted.get());
        assertEquals("meeting-service", body.path("user").path("uid").asText());
        assertEquals("200", body.path("request").path("ssd_version").asText());
        assertEquals("bigmodel", body.path("request").path("model_name").asText());
        assertTrue(body.path("request").path("show_utterances").asBoolean());
        assertTrue(body.path("request").path("enable_speaker_info").asBoolean());
    }
    @Test void silenceCompletesWithoutFabricatingText() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v3/auc/bigmodel/query",exchange->{exchange.getResponseHeaders().add("X-Api-Status-Code","20000003");exchange.sendResponseHeaders(200,-1);exchange.close();});
        server.start();
        var client=new VolcTranscriptionClient(HttpClient.newHttpClient(),new ObjectMapper(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-key","volc.seedasr.auc");
        var result=client.query(UUID.randomUUID());assertEquals(VolcTranscriptionClient.State.SUCCEEDED,result.state());assertTrue(result.segments().isEmpty());assertEquals("",result.rawResponse());
    }
}
