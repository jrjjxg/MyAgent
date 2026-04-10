package com.xg.platform.tooling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;

public class BuiltinWeatherClient {

    private static final String PRIMARY_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FALLBACK_GEOCODING_URL = "https://nominatim.openstreetmap.org/search";
    private static final String FORECAST_API_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String WEATHER_USER_AGENT = "deepagents-weather/1.0";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String primaryGeocodingUrl;
    private final String fallbackGeocodingUrl;
    private final String forecastApiUrl;

    public BuiltinWeatherClient(ObjectMapper objectMapper, Duration requestTimeout) {
        this(objectMapper, requestTimeout, PRIMARY_GEOCODING_URL, FALLBACK_GEOCODING_URL, FORECAST_API_URL);
    }

    BuiltinWeatherClient(ObjectMapper objectMapper,
                         Duration requestTimeout,
                         String primaryGeocodingUrl,
                         String fallbackGeocodingUrl,
                         String forecastApiUrl) {
        this.objectMapper = objectMapper;
        this.requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                ? Duration.ofSeconds(15)
                : requestTimeout;
        this.primaryGeocodingUrl = primaryGeocodingUrl;
        this.fallbackGeocodingUrl = fallbackGeocodingUrl;
        this.forecastApiUrl = forecastApiUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public JsonNode forecast(String location, Integer dayOffset, Integer days) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("weather requires location");
        }
        int normalizedOffset = Math.max(0, Math.min(dayOffset == null ? 0 : dayOffset, 2));
        int normalizedDays = Math.max(1, Math.min(days == null ? 1 : days, 3));
        String requestedLocation = location.trim();
        ResolvedLocation resolvedLocation = resolveLocation(requestedLocation);
        int forecastDaysRequested = Math.max(1, Math.min(16, normalizedOffset + normalizedDays));
        String forecastUrl = forecastApiUrl
                + "?latitude=" + resolvedLocation.latitude()
                + "&longitude=" + resolvedLocation.longitude()
                + "&daily=" + encode("weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_direction_10m_dominant")
                + "&timezone=auto"
                + "&forecast_days=" + forecastDaysRequested;
        JsonNode payload = requestJson(
                forecastUrl,
                Map.of("Accept", "application/json"),
                "weather forecast"
        );
        JsonNode daily = payload.path("daily");
        JsonNode dates = daily.path("time");
        if (!dates.isArray() || dates.isEmpty()) {
            throw new IllegalStateException("weather returned no forecast data");
        }

        int fromIndex = Math.min(normalizedOffset, dates.size() - 1);
        int toExclusive = Math.min(dates.size(), fromIndex + normalizedDays);
        ArrayNode forecastDays = objectMapper.createArrayNode();
        for (int index = fromIndex; index < toExclusive; index++) {
            ObjectNode normalizedDay = forecastDays.addObject();
            normalizedDay.put("date", dates.path(index).asText(""));
            normalizedDay.put("condition", describeWeatherCode(readIntArrayValue(daily.path("weather_code"), index, "weather_code")));
            normalizedDay.put("temperatureMin", formatNumber(readDoubleArrayValue(daily.path("temperature_2m_min"), index, "temperature_2m_min")));
            normalizedDay.put("temperatureMax", formatNumber(readDoubleArrayValue(daily.path("temperature_2m_max"), index, "temperature_2m_max")));
            normalizedDay.put("wind", summarizeWind(
                    readOptionalDoubleArrayValue(daily.path("wind_direction_10m_dominant"), index),
                    readOptionalDoubleArrayValue(daily.path("wind_speed_10m_max"), index)
            ));
            normalizedDay.put("precipitation", summarizePrecipitation(
                    readOptionalDoubleArrayValue(daily.path("precipitation_probability_max"), index),
                    readOptionalDoubleArrayValue(daily.path("precipitation_sum"), index)
            ));
        }

        JsonNode primaryDay = forecastDays.get(0);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("location", resolvedLocation.name());
        result.put("dayOffset", normalizedOffset);
        result.put("days", forecastDays.size());
        result.set("forecastDays", forecastDays);
        result.put("condition", primaryDay.path("condition").asText(""));
        result.put("temperatureMin", primaryDay.path("temperatureMin").asText(""));
        result.put("temperatureMax", primaryDay.path("temperatureMax").asText(""));
        result.put("wind", primaryDay.path("wind").asText(""));
        result.put("precipitation", primaryDay.path("precipitation").asText(""));
        ObjectNode source = result.putObject("source");
        source.put("provider", "open-meteo");
        source.put("title", "Weather forecast for " + resolvedLocation.name());
        source.put("domain", hostOf(forecastApiUrl));
        source.put("url", forecastUrl);
        source.put("geocoder", resolvedLocation.geocoder());
        return result;
    }

    private ResolvedLocation resolveLocation(String location) {
        for (String candidate : locationCandidates(location)) {
            ResolvedLocation resolved = geocodeWithOpenMeteo(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        for (String candidate : locationCandidates(location)) {
            ResolvedLocation resolved = geocodeWithNominatim(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        throw new IllegalStateException("weather returned no matching location");
    }

    private Iterable<String> locationCandidates(String location) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String trimmed = location.trim();
        candidates.add(trimmed);
        String strippedChineseSuffix = trimmed.replaceFirst("[\\u5e02\\u533a\\u53bf\\u7701]$", "").trim();
        if (!strippedChineseSuffix.isBlank()) {
            candidates.add(strippedChineseSuffix);
        }
        String strippedEnglishSuffix = trimmed.replaceFirst("(?i)\\s+city$", "").trim();
        if (!strippedEnglishSuffix.isBlank()) {
            candidates.add(strippedEnglishSuffix);
        }
        return candidates;
    }

    private ResolvedLocation geocodeWithOpenMeteo(String location) {
        String url = primaryGeocodingUrl
                + "?name=" + encode(location)
                + "&count=5"
                + "&format=json";
        JsonNode payload = requestJson(
                url,
                Map.of("Accept", "application/json"),
                "weather geocoding"
        );
        JsonNode results = payload.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        for (JsonNode result : results) {
            Double latitude = readOptionalDouble(result.path("latitude"));
            Double longitude = readOptionalDouble(result.path("longitude"));
            if (latitude == null || longitude == null) {
                continue;
            }
            String name = bestLocationName(result.path("name").asText(""), result.path("country").asText(""));
            return new ResolvedLocation(name, latitude, longitude, "open-meteo");
        }
        return null;
    }

    private ResolvedLocation geocodeWithNominatim(String location) {
        String url = fallbackGeocodingUrl
                + "?q=" + encode(location)
                + "&format=jsonv2"
                + "&limit=5";
        JsonNode payload = requestJson(
                url,
                Map.of(
                        "Accept", "application/json",
                        "User-Agent", WEATHER_USER_AGENT
                ),
                "weather geocoding"
        );
        if (!payload.isArray() || payload.isEmpty()) {
            return null;
        }
        for (JsonNode result : payload) {
            Double latitude = readOptionalDouble(result.path("lat"));
            Double longitude = readOptionalDouble(result.path("lon"));
            if (latitude == null || longitude == null) {
                continue;
            }
            String name = result.path("name").asText("").trim();
            if (name.isBlank()) {
                name = result.path("display_name").asText(location).trim();
            }
            return new ResolvedLocation(name, latitude, longitude, "nominatim");
        }
        return null;
    }

    private JsonNode requestJson(String url, Map<String, String> headers, String requestLabel) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(requestTimeout);
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(requestLabel + " failed with status " + response.statusCode());
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new IllegalStateException(requestLabel + " returned empty data");
            }
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read weather response", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Weather request interrupted", exception);
        }
    }

    private int readIntArrayValue(JsonNode values, int index, String fieldName) {
        if (!values.isArray() || index >= values.size()) {
            throw new IllegalStateException("weather response missing " + fieldName);
        }
        return values.get(index).asInt();
    }

    private double readDoubleArrayValue(JsonNode values, int index, String fieldName) {
        Double value = readOptionalDoubleArrayValue(values, index);
        if (value == null) {
            throw new IllegalStateException("weather response missing " + fieldName);
        }
        return value;
    }

    private Double readOptionalDoubleArrayValue(JsonNode values, int index) {
        if (!values.isArray() || index >= values.size()) {
            return null;
        }
        return readOptionalDouble(values.get(index));
    }

    private Double readOptionalDouble(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String text = valueNode.asText("").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String bestLocationName(String name, String country) {
        String trimmedName = name == null ? "" : name.trim();
        String trimmedCountry = country == null ? "" : country.trim();
        if (trimmedName.isBlank()) {
            return trimmedCountry.isBlank() ? "the requested location" : trimmedCountry;
        }
        if (trimmedCountry.isBlank()) {
            return trimmedName;
        }
        return trimmedName + ", " + trimmedCountry;
    }

    private String summarizeWind(Double directionDegrees, Double speed) {
        if (directionDegrees == null && speed == null) {
            return "";
        }
        String direction = directionDegrees == null ? "" : directionToCompass(directionDegrees);
        String formattedSpeed = speed == null ? "" : formatNumber(speed) + " km/h";
        if (direction.isBlank()) {
            return formattedSpeed;
        }
        if (formattedSpeed.isBlank()) {
            return direction;
        }
        return direction + " " + formattedSpeed;
    }

    private String summarizePrecipitation(Double chance, Double amount) {
        if (chance == null && amount == null) {
            return "";
        }
        String chanceText = chance == null ? "" : formatNumber(chance) + "% chance";
        String amountText = amount == null || amount <= 0 ? "" : formatNumber(amount) + " mm";
        if (chanceText.isBlank()) {
            return amountText;
        }
        if (amountText.isBlank()) {
            return chanceText;
        }
        return chanceText + ", " + amountText;
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private String directionToCompass(double degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.floor((degrees + 11.25) / 22.5) % directions.length;
        return directions[index];
    }

    private String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Freezing drizzle";
            case 61 -> "Light rain";
            case 63 -> "Rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Freezing rain";
            case 71 -> "Light snow";
            case 73 -> "Snow";
            case 75 -> "Heavy snow";
            case 77 -> "Snow grains";
            case 80 -> "Rain showers";
            case 81 -> "Heavy rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96 -> "Thunderstorm with hail";
            case 99 -> "Severe thunderstorm with hail";
            default -> "Unknown";
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String hostOf(String value) {
        return URI.create(value).getHost();
    }

    private record ResolvedLocation(
            String name,
            double latitude,
            double longitude,
            String geocoder
    ) {
    }
}
