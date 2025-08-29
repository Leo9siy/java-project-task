package app;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class manager {
	
	private static final String BASE = "https://api.weatherapi.com/v1/forecast.json";
	
    public static String getApiKey(String[] args) {
        String key = System.getenv("WEATHERAPI_KEY");
        if (key != null && !key.isBlank()) return key;

        for (String a : args) {
            if (a.startsWith("--key=")) return a.substring("--key=".length());
        }
        return null;
    }

    public static String buildUrl(String key, String city) {
        String q = URLEncoder.encode(city, StandardCharsets.UTF_8);
        return new StringBuilder().append(BASE).append("?key=").append(key)
        		.append("&q=").append(q).append("&days=2&aqi=no&alerts=no").toString();
    }

    public static JsonNode fetch(HttpClient http, String url, ObjectMapper om) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + res.statusCode() + " for URL: " + url + " body: " + res.body());
        }
        return om.readTree(res.body());
    }
}
