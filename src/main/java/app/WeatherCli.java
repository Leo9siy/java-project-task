package app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class WeatherCli {

    private static final String[] CITIES = {"Chisinau", "Madrid", "Kyiv", "Amsterdam"};

    record ForecastRow(
    		String city, Map<String, String> weatherData
    ) {}

    public static void main(String[] args) throws Exception {
        String key = manager.getApiKey(args);
        if (key == null || key.isBlank()) {
            System.err.println("ERROR: Provide WeatherAPI key via env WEATHERAPI_KEY or --key=YOUR_KEY");
            System.exit(1);
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        List<ForecastRow> rows = new ArrayList<ForecastRow>();
        Set<String> dates = new HashSet<String>();

        for (String city : CITIES) {
            String url = manager.buildUrl(key, city);
            
            JsonNode root = fetch(httpClient, url, objectMapper);

            JsonNode forecastDays = root.path("forecast").path("forecastday");
            if (!forecastDays.isArray() || forecastDays.size() == 0) {
                throw new RuntimeException("No forecast data for " + city);
            }
            
            HashMap<String, String> weatherData = new HashMap<String, String>();
            
            for (JsonNode dayNode : forecastDays) {
            	String date = dayNode.path("date").asText();
            	dates.add(date);
            	JsonNode day = dayNode.path("day");
                double minC = day.path("mintemp_c").asDouble();
                double maxC = day.path("maxtemp_c").asDouble();
                double avgHumidity = day.path("avghumidity").asDouble();
                double maxWindKph = day.path("maxwind_kph").asDouble();

                String windDir = computePredominantWindDir(dayNode.path("hour"));
                
                String weather = String.format("min %.1f / max %.1f °C | hum %.0f%% | wind %.1f kph %s",
                        minC, maxC, avgHumidity, maxWindKph, windDir);
                weatherData.put(date, weather);
            }
            rows.add(new ForecastRow(city, weatherData));
        }

        printTable(rows, dates);
    }

    private static JsonNode fetch(HttpClient http, String url, ObjectMapper om) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + res.statusCode() + " for URL: " + url + " body: " + res.body());
        }
        return om.readTree(res.body());
    }

    private static String computePredominantWindDir(JsonNode hourly) {
        if (!hourly.isArray() || hourly.size() == 0) return "N/A";

        double sumSin = 0.0, sumCos = 0.0;
        for (JsonNode h : hourly) {
            double deg = h.path("wind_degree").asDouble();
            double rad = Math.toRadians(deg);
            sumSin += Math.sin(rad);
            sumCos += Math.cos(rad);
        }
        double meanRad = Math.atan2(sumSin, sumCos);
        double meanDeg = Math.toDegrees(meanRad);
        if (meanDeg < 0) meanDeg += 360.0;
        return degreesToCompass(meanDeg);
    }

    private static String degreesToCompass(double deg) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        int idx = (int)Math.round(((deg % 360) / 22.5));
        return dirs[idx % 16];
    }

    private static void printTable(List<ForecastRow> rows, Set<String> dates) {
        String header = String.format("%-10s", "City");
        for (String date : dates) {
            header += String.format(" | %-50s", date);
        }
        String sep = "-".repeat(header.length());
        System.out.println(header);
        System.out.println(sep);

        rows.stream()
            .sorted(Comparator.comparing(ForecastRow::city))
            .forEach(row -> {
                var rowData = String.format("%-10s", row.city);
                for (String date : dates) {
                    var weather = row.weatherData.getOrDefault(date, "No data");
                    rowData += String.format(" | %-20s", weather);
                }
                System.out.println(rowData);
            });
    }
}
