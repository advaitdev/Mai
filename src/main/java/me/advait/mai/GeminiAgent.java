package me.advait.mai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GeminiAgent {

    private static final GeminiAgent INSTANCE = new GeminiAgent();

    public static GeminiAgent getInstance() {
        return INSTANCE;
    }

    private HttpClient httpClient = HttpClient.newHttpClient();
    private final String API_KEY = "AIzaSyDXuCFnSQnCVuKX_FwKtNXg7BoEyoOA2pg";

    private final List<JsonObject> history = new ArrayList<>();

    // Method to add interactions to contents
    public void addToHistory(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.addProperty("role", role);
        content.add("parts", parts);

        history.add(content);
    }

    public String askAndReceive(String textPrompt) {
        addToHistory("user", textPrompt);

        Gson gson = new Gson();
        JsonObject requestBodyJson = new JsonObject();

        if (!history.isEmpty()) {
            requestBodyJson.add("contents", gson.toJsonTree(history));
        }

        String requestBody = gson.toJson(requestBodyJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + this.API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

                String modelResponse = extractModelResponse(jsonResponse);
                addToHistory("model", modelResponse);
                return modelResponse;
            } else {
                return "Error: " + response.statusCode() + " - " + response.body();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }

    private String extractModelResponse(JsonObject jsonResponse) {
        // Initialize StringBuilder to accumulate the text parts
        StringBuilder responseText = new StringBuilder();

        // Get the "candidates" array from the JSON response
        JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
        if (candidates != null && candidates.size() > 0) {
            // Assuming we're only interested in the first candidate for simplicity
            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            if (content != null) {
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts != null) {
                    for (JsonElement partElement : parts) {
                        JsonObject part = partElement.getAsJsonObject();
                        String text = part.get("text").getAsString(); // Extract the text
                        responseText.append(text); // Append the extracted text to our StringBuilder
                    }
                }
            }
        }

        return responseText.toString(); // Return the accumulated text
    }

}



