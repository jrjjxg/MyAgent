package com.xg.platform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinWebResearchClientTest {

    @Test
    void fetchUsesBrowserStyleHeadersByDefault() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AtomicReference<Headers> capturedHeaders = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/page", exchange -> {
            capturedHeaders.set(exchange.getRequestHeaders());
            writeResponse(exchange, 200, "text/html; charset=UTF-8", "<html><body>Hello browser profile</body></html>");
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            BuiltinWebResearchClient client = new BuiltinWebResearchClient(
                    objectMapper,
                    "duckduckgo",
                    "https://api.duckduckgo.com/",
                    "",
                    "",
                    Duration.ofSeconds(5),
                    5,
                    false
            );

            var result = client.fetch(url);

            assertThat(result.path("text").asText()).contains("Hello browser profile");
            assertThat(capturedHeaders.get().getFirst("User-Agent")).contains("Mozilla/5.0");
            assertThat(capturedHeaders.get().getFirst("Accept-Language")).isEqualTo("en-US,en;q=0.9");
            assertThat(capturedHeaders.get().getFirst("Upgrade-Insecure-Requests")).isEqualTo("1");
            assertThat(capturedHeaders.get().getFirst("Referer")).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchRetries403WithBrowserFallbackAndCookies() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> firstUserAgent = new AtomicReference<>();
        AtomicReference<String> secondUserAgent = new AtomicReference<>();
        AtomicReference<String> secondCookie = new AtomicReference<>();
        AtomicReference<String> secondReferer = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/blocked", exchange -> {
            int currentRequest = requestCount.incrementAndGet();
            if (currentRequest == 1) {
                firstUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                exchange.getResponseHeaders().add("Set-Cookie", "clearance=1; Path=/");
                writeResponse(exchange, 403, "text/plain; charset=UTF-8", "blocked");
                return;
            }
            secondUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            secondCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
            secondReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            writeResponse(exchange, 200, "text/html; charset=UTF-8", "<html><body>Recovered after retry</body></html>");
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            String url = "http://127.0.0.1:" + port + "/blocked";
            BuiltinWebResearchClient client = new BuiltinWebResearchClient(
                    objectMapper,
                    "duckduckgo",
                    "https://api.duckduckgo.com/",
                    "",
                    "myagent-web-research/1.0",
                    Duration.ofSeconds(5),
                    5,
                    false
            );

            var result = client.fetch(url);

            assertThat(result.path("text").asText()).contains("Recovered after retry");
            assertThat(requestCount.get()).isEqualTo(2);
            assertThat(firstUserAgent.get()).isEqualTo("myagent-web-research/1.0");
            assertThat(secondUserAgent.get()).contains("Mozilla/5.0");
            assertThat(secondCookie.get()).contains("clearance=1");
            assertThat(secondReferer.get()).isEqualTo("http://127.0.0.1:" + port + "/");
        } finally {
            server.stop(0);
        }
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        } finally {
            exchange.close();
        }
    }
}
