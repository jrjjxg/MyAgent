package com.xg.platform.tooling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import com.xg.platform.tooling.port.WebSearchSettingsResolver;

public class BuiltinWebResearchClient {

    private static final Logger logger = Logger.getLogger(BuiltinWebResearchClient.class.getName());

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final String DEFAULT_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final String FALLBACK_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0";
    private static final String DEFAULT_ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final Set<Integer> RETRYABLE_FETCH_STATUS_CODES = Set.of(403, 406, 429, 503);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WebSearchSettingsResolver settingsResolver;
    private final String defaultProvider;
    private final String defaultSearchApiBaseUrl;
    private final String defaultTavilyApiKey;
    private final String userAgent;
    private final Duration requestTimeout;
    private final int defaultMaxResults;
    private final boolean logAgentFlow;

    public BuiltinWebResearchClient(ObjectMapper objectMapper,
                                    String provider,
                                    String searchApiBaseUrl,
                                    String tavilyApiKey,
                                    String userAgent,
                                    Duration timeout,
                                    int defaultMaxResults,
                                    boolean logAgentFlow) {
        this(
                objectMapper,
                userId -> new WebSearchSettingsResolver.ResolvedWebSearchSettings(provider, searchApiBaseUrl, tavilyApiKey),
                userAgent,
                timeout,
                defaultMaxResults,
                logAgentFlow
        );
    }

    public BuiltinWebResearchClient(ObjectMapper objectMapper,
                                    WebSearchSettingsResolver settingsResolver,
                                    String userAgent,
                                    Duration timeout,
                                    int defaultMaxResults,
                                    boolean logAgentFlow) {
        this.requestTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(20)
                : timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();
        this.objectMapper = objectMapper;
        this.settingsResolver = settingsResolver;
        this.defaultProvider = "duckduckgo";
        this.defaultSearchApiBaseUrl = "https://api.duckduckgo.com/";
        this.defaultTavilyApiKey = "";
        this.userAgent = userAgent == null || userAgent.isBlank()
                ? DEFAULT_BROWSER_USER_AGENT
                : userAgent;
        this.defaultMaxResults = defaultMaxResults <= 0 ? 5 : defaultMaxResults;
        this.logAgentFlow = logAgentFlow;
    }

    public JsonNode search(String query, Integer maxResults) {
        return search(null, query, maxResults);
    }

    public JsonNode search(String userId, String query, Integer maxResults) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("web_search requires a non-blank query");
        }
        int limit = maxResults == null ? defaultMaxResults : Math.max(1, Math.min(maxResults, 8));
        WebSearchSettingsResolver.ResolvedWebSearchSettings resolved = resolveSettings(userId);
        String provider = normalizeProvider(resolved.provider(), resolved.tavilyApiKey());
        return switch (provider) {
            case "tavily" -> tavilySearch(query, limit, resolved);
            case "duckduckgo" -> duckDuckGoSearch(query, limit, resolved);
            default -> throw new IllegalStateException("Unsupported web search provider: " + provider);
        };
    }

    private JsonNode duckDuckGoSearch(String query, int limit, WebSearchSettingsResolver.ResolvedWebSearchSettings resolved) {
        String url = resolveSearchApiBaseUrl(resolved.searchApiBaseUrl())
                + "?q=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                + "&format=json&no_html=1&skip_disambig=0";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .build();
        try {
            log(() -> "web_search provider=duckduckgo query=" + truncate(query, 160) + " limit=" + limit);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                logger.warning(() -> "web_search provider=duckduckgo failed"
                        + " query=" + truncate(query, 160)
                        + " limit=" + limit
                        + " url=" + url
                        + " status=" + response.statusCode()
                        + " body=" + truncate(response.body(), 400));
                throw new IllegalStateException("web_search failed with status " + response.statusCode());
            }
            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode normalized = normalizeSearchResults(query, payload, limit);
            log(() -> "web_search provider=duckduckgo results=" + normalized.path("count").asInt(0));
            return normalized;
        } catch (IOException exception) {
            logger.log(Level.WARNING, "web_search provider=duckduckgo failed with I/O error"
                    + " query=" + truncate(query, 160)
                    + " limit=" + limit
                    + " url=" + url, exception);
            throw new UncheckedIOException("Failed to execute web search", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "web_search provider=duckduckgo interrupted"
                    + " query=" + truncate(query, 160)
                    + " limit=" + limit
                    + " url=" + url, exception);
            throw new IllegalStateException("Web search interrupted", exception);
        }
    }

    private JsonNode tavilySearch(String query, int limit, WebSearchSettingsResolver.ResolvedWebSearchSettings resolved) {
        String tavilyApiKey = normalizeSecret(resolved.tavilyApiKey());
        if (tavilyApiKey.isBlank()) {
            logger.warning(() -> "web_search provider=tavily aborted because API key is missing"
                    + " query=" + truncate(query, 160)
                    + " limit=" + limit);
            throw new IllegalStateException("Tavily API key is missing");
        }
        String url = resolveTavilyBaseUrl(resolved.searchApiBaseUrl());
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(buildTavilyPayload(query, limit)))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", userAgent)
                .header("Authorization", "Bearer " + tavilyApiKey)
                .build();
        try {
            log(() -> "web_search provider=tavily query=" + truncate(query, 160) + " limit=" + limit);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                logger.warning(() -> "web_search provider=tavily failed"
                        + " query=" + truncate(query, 160)
                        + " limit=" + limit
                        + " url=" + url
                        + " status=" + response.statusCode()
                        + " body=" + truncate(response.body(), 400));
                throw new IllegalStateException("web_search failed with status " + response.statusCode() + " body=" + truncate(response.body(), 400));
            }
            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode normalized = normalizeTavilyResults(query, payload, limit);
            log(() -> "web_search provider=tavily results=" + normalized.path("count").asInt(0));
            return normalized;
        } catch (IOException exception) {
            logger.log(Level.WARNING, "web_search provider=tavily failed with I/O error"
                    + " query=" + truncate(query, 160)
                    + " limit=" + limit
                    + " url=" + url, exception);
            throw new UncheckedIOException("Failed to execute Tavily web search", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "web_search provider=tavily interrupted"
                    + " query=" + truncate(query, 160)
                    + " limit=" + limit
                    + " url=" + url, exception);
            throw new IllegalStateException("Tavily web search interrupted", exception);
        }
    }

    public JsonNode fetch(String url) {
        URI uri = validateHttpUrl(url);
        try {
            log(() -> "web_fetch url=" + uri);
            HttpResponse<String> response = sendFetchRequest(uri, userAgent, false);
            if (shouldRetryFetch(response.statusCode())) {
                int initialStatus = response.statusCode();
                String retryUserAgent = fallbackUserAgent();
                log(() -> "web_fetch retry url=" + uri + " status=" + initialStatus + " strategy=browser-profile");
                response = sendFetchRequest(uri, retryUserAgent, true);
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("web_fetch failed with status " + response.statusCode()
                        + " url=" + uri
                        + " body=" + truncate(response.body(), 280));
            }
            String body = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("text/plain");
            String text = contentType.contains("html") ? htmlToText(body) : body;
            ObjectNode result = objectMapper.createObjectNode();
            result.put("url", uri.toString());
            result.put("contentType", contentType);
            result.put("text", truncate(text, 12000));
            result.put("fetchedAt", System.currentTimeMillis());
            log(() -> "web_fetch completed url=" + uri + " contentType=" + contentType + " textChars=" + result.path("text").asText("").length());
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to fetch web content", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Web fetch interrupted", exception);
        }
    }

    private HttpResponse<String> sendFetchRequest(URI uri, String requestUserAgent, boolean addSameOriginReferer)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5")
                .header("Accept-Language", DEFAULT_ACCEPT_LANGUAGE)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
                .header("User-Agent", requestUserAgent);
        if (addSameOriginReferer) {
            builder.header("Referer", originOf(uri));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private boolean shouldRetryFetch(int statusCode) {
        return RETRYABLE_FETCH_STATUS_CODES.contains(statusCode);
    }

    private String fallbackUserAgent() {
        return hasBrowserLikeUserAgent(userAgent) ? userAgent : FALLBACK_BROWSER_USER_AGENT;
    }

    private boolean hasBrowserLikeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("mozilla/5.0")
                || normalized.contains("chrome/")
                || normalized.contains("safari/")
                || normalized.contains("firefox/");
    }

    private JsonNode normalizeSearchResults(String query, JsonNode payload, int limit) {
        ArrayNode results = objectMapper.createArrayNode();
        Set<String> seenUrls = new LinkedHashSet<>();

        addDirectResult(results, seenUrls, payload.path("AbstractURL").asText(""), payload.path("Heading").asText(""), payload.path("AbstractText").asText(""), limit);
        for (JsonNode result : payload.path("Results")) {
            addTopic(results, seenUrls, result, limit);
        }
        for (JsonNode topic : payload.path("RelatedTopics")) {
            if (topic.has("Topics")) {
                for (JsonNode nested : topic.path("Topics")) {
                    addTopic(results, seenUrls, nested, limit);
                    if (results.size() >= limit) {
                        break;
                    }
                }
            } else {
                addTopic(results, seenUrls, topic, limit);
            }
            if (results.size() >= limit) {
                break;
            }
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("query", query);
        response.put("provider", "duckduckgo-instant-answer");
        response.set("results", results);
        response.put("count", results.size());
        return response;
    }

    private JsonNode normalizeTavilyResults(String query, JsonNode payload, int limit) {
        ArrayNode results = objectMapper.createArrayNode();
        int index = 0;
        for (JsonNode result : payload.path("results")) {
            if (results.size() >= limit) {
                break;
            }
            String url = result.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            URI uri = validateHttpUrl(url);
            ObjectNode item = results.addObject();
            item.put("sourceId", "web-" + index++);
            item.put("title", result.path("title").asText(uri.getHost()));
            item.put("url", uri.toString());
            item.put("snippet", truncate(result.path("content").asText(""), 400));
        }
        ObjectNode response = objectMapper.createObjectNode();
        response.put("query", query);
        response.put("provider", "tavily");
        response.set("results", results);
        response.put("count", results.size());
        return response;
    }

    private void addTopic(ArrayNode results, Set<String> seenUrls, JsonNode node, int limit) {
        if (results.size() >= limit) {
            return;
        }
        String url = node.path("FirstURL").asText("");
        String text = node.path("Text").asText("");
        addDirectResult(results, seenUrls, url, extractTitle(text, url), text, limit);
    }

    private void addDirectResult(ArrayNode results, Set<String> seenUrls, String url, String title, String snippet, int limit) {
        if (results.size() >= limit || url == null || url.isBlank()) {
            return;
        }
        URI uri = validateHttpUrl(url);
        if (!seenUrls.add(uri.toString())) {
            return;
        }
        ObjectNode item = results.addObject();
        item.put("sourceId", "web-" + results.size());
        item.put("title", title == null || title.isBlank() ? uri.getHost() : title.trim());
        item.put("url", uri.toString());
        item.put("snippet", truncate(snippet == null ? "" : snippet.trim(), 400));
    }

    private URI validateHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("web_fetch requires a non-blank url");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Only http and https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL must include a host");
        }
        return uri;
    }

    private String originOf(URI uri) {
        StringBuilder builder = new StringBuilder();
        builder.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            builder.append(":").append(uri.getPort());
        }
        builder.append("/");
        return builder.toString();
    }

    private String extractTitle(String text, String fallbackUrl) {
        if (text == null || text.isBlank()) {
            return fallbackUrl;
        }
        int separator = text.indexOf(" - ");
        return separator > 0 ? text.substring(0, separator).trim() : truncate(text.trim(), 80);
    }

    private String resolveTavilyBaseUrl(String configuredBaseUrl) {
        String searchApiBaseUrl = resolveSearchApiBaseUrl(configuredBaseUrl);
        if (searchApiBaseUrl != null && !searchApiBaseUrl.isBlank() && !"https://api.duckduckgo.com/".equals(searchApiBaseUrl)) {
            return searchApiBaseUrl;
        }
        return "https://api.tavily.com/search";
    }

    private WebSearchSettingsResolver.ResolvedWebSearchSettings resolveSettings(String userId) {
        WebSearchSettingsResolver.ResolvedWebSearchSettings resolved = settingsResolver == null
                ? null
                : settingsResolver.resolve(userId);
        String provider = resolved == null ? null : resolved.provider();
        String searchApiBaseUrl = resolved == null ? null : resolved.searchApiBaseUrl();
        String tavilyApiKey = resolved == null ? null : resolved.tavilyApiKey();
        return new WebSearchSettingsResolver.ResolvedWebSearchSettings(
                provider == null || provider.isBlank() ? defaultProvider : provider,
                searchApiBaseUrl == null || searchApiBaseUrl.isBlank() ? defaultSearchApiBaseUrl : searchApiBaseUrl,
                tavilyApiKey == null ? defaultTavilyApiKey : tavilyApiKey
        );
    }

    private String normalizeProvider(String provider, String tavilyApiKey) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase();
        if (normalizedProvider.isBlank() || "auto".equals(normalizedProvider)) {
            return normalizeSecret(tavilyApiKey).isBlank() ? "duckduckgo" : "tavily";
        }
        return normalizedProvider;
    }

    private String normalizeSecret(String value) {
        return value == null ? "" : value.trim();
    }

    private String resolveSearchApiBaseUrl(String configuredBaseUrl) {
        return configuredBaseUrl == null || configuredBaseUrl.isBlank()
                ? defaultSearchApiBaseUrl
                : configuredBaseUrl.trim();
    }

    private String buildTavilyPayload(String query, int limit) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("query", query.trim());
        payload.put("max_results", limit);
        payload.put("search_depth", "advanced");
        payload.put("include_answer", false);
        payload.put("include_raw_content", false);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to serialize Tavily search payload", exception);
        }
    }

    private String htmlToText(String html) {
        String text = html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        text = TAG_PATTERN.matcher(text).replaceAll(" ");
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ").replace('\u00A0', ' ').trim();
        return text;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private void log(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}
