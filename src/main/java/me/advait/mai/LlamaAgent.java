package me.advait.mai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class LlamaAgent {

    private static final LlamaAgent INSTANCE = new LlamaAgent();

    public static LlamaAgent getInstance() {
        return INSTANCE;
    }

    private HttpClient httpClient = HttpClient.newHttpClient();
    private final String API_URL = "http://192.168.1.57:11434/api/generate";
    private final String MODEL_NAME = "llama3";

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
        requestBodyJson.addProperty("model", MODEL_NAME);
        requestBodyJson.addProperty("prompt", textPrompt);
        requestBodyJson.addProperty("stream", false);

        String requestBody = gson.toJson(requestBodyJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                try {
                    JsonObject jsonResponse = parseJson(response.body());
                    String modelResponse = extractModelResponse(jsonResponse);
                    addToHistory("model", modelResponse);
                    return modelResponse;
                } catch (JsonSyntaxException e) {
                    return "Error parsing response: " + e.getMessage();
                }
            } else {
                return "Error: " + response.statusCode() + " - " + response.body();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }

    private JsonObject parseJson(String responseBody) throws JsonSyntaxException {
        Gson gson = new GsonBuilder().setLenient().create();
        JsonReader reader = new JsonReader(new StringReader(responseBody));
        reader.setLenient(true);
        return gson.fromJson(reader, JsonObject.class);
    }

    private String extractModelResponse(JsonObject jsonResponse) {
        return jsonResponse.get("response").getAsString();
    }
}
