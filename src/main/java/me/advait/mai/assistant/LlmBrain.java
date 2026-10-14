package me.advait.mai.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optional Claude-driven chat: replaces the {@link ChatIntents} keyword rules
 * with a model that reads the player's message plus an {@link Observation},
 * calls {@link Tools} (the same registry commands use) and answers in chat.
 *
 * <p>Off unless {@code assistant.llm.enabled} is true and an API key is set
 * (config or {@code ANTHROPIC_API_KEY}). Each chat message is a fresh
 * conversation seeded with the last few lines said to this humanoid, so no
 * long history is resent. Network calls run on a worker thread; observations
 * and tool calls hop to the server thread.
 */
public final class LlmBrain {

    private static final String SYSTEM = """
            You are %s, a helpful companion living in a Minecraft world as a player-like body.
            Players talk to you in chat. Act by calling tools; whatever plain text you write is
            said aloud in chat as you, so keep it to one or two short, friendly sentences and
            never use markdown. Don't narrate tool calls or repeat tool output verbatim.

            How you work:
            - "me", "here" and "this" refer to the player speaking. mine/place with no coordinates
              use the block that player is looking at; come/follow/guard default to them.
            - follow, guard and attack keep running after the tool returns; one-shot jobs (goto,
              come, mine, place, give) report when done or when they're still in progress.
            - Starting any job replaces the current one. stop ends everything.
            - If a tool fails, say briefly why in plain words. If nothing fits the request, say
              what you can do instead rather than guessing.
            - Only act for the speaking player. You can't fly, craft or chat with other servers.
            """;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Mai-LLM");
        t.setDaemon(true);
        return t;
    });

    /** Recent lines per humanoid ("<player> text" / "<Mai> reply"), oldest first. */
    private static final Map<UUID, List<String>> RECENT = new ConcurrentHashMap<>();
    private static final int RECENT_LINES = 8;

    private static AnthropicClient client;
    private static String clientKey;

    private LlmBrain() {}

    /** Whether chat should go to the model rather than the keyword rules. */
    public static boolean available() {
        return AssistantConfig.llmEnabled() && !apiKey().isEmpty();
    }

    private static String apiKey() {
        String k = AssistantConfig.llmApiKey();
        if (k.isEmpty()) {
            String env = System.getenv("ANTHROPIC_API_KEY");
            k = env == null ? "" : env.trim();
        }
        return k;
    }

    private static synchronized AnthropicClient client() {
        String key = apiKey();
        if (client == null || !key.equals(clientKey)) {
            client = AnthropicOkHttpClient.builder().apiKey(key).build();
            clientKey = key;
        }
        return client;
    }

    /**
     * Handle one chat instruction. Call on the server thread. {@code onFail}
     * runs (on the server thread) if the model can't be reached, so the
     * caller can fall back to the keyword rules.
     */
    public static void handle(Humanoid h, CommandSender speaker, String text, Runnable onFail) {
        Assistant assistant = Assistant.of(h);
        String observation = Observation.of(h).toString();
        List<String> recent = RECENT.computeIfAbsent(h.getUuid(), id -> new ArrayList<>());
        String history;
        synchronized (recent) {
            history = String.join("\n", recent);
            remember(recent, "<" + speaker.getName() + "> " + text);
        }
        String user = (history.isEmpty() ? "" : "Recent chat with you:\n" + history + "\n\n")
                + "Your current state (JSON):\n" + observation + "\n\n"
                + speaker.getName() + " says to you: " + text;

        WORKER.submit(() -> {
            try {
                converse(h, speaker, user, recent);
            } catch (Exception ex) {
                Mai.getInstance().getLogger().warning("[LLM] " + h.getName() + ": " + ex);
                Bukkit.getScheduler().runTask(Mai.getInstance(), onFail);
            }
        });
    }

    private static void converse(Humanoid h, CommandSender speaker, String user, List<String> recent) throws Exception {
        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(user).build());
        int maxSteps = AssistantConfig.llmMaxSteps();

        for (int step = 0; step < maxSteps; step++) {
            Message resp = client().messages().create(params(h, messages));
            StopReason stop = resp.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stop)) throw new IllegalStateException("model refused");

            List<ContentBlockParam> results = new ArrayList<>();
            for (ContentBlock block : resp.content()) {
                block.text().ifPresent(t -> speak(h, t.text(), recent));
                if (block.toolUse().isPresent()) {
                    results.add(runTool(h, speaker, block.toolUse().get()));
                }
            }
            if (results.isEmpty() || !StopReason.TOOL_USE.equals(stop)) return;
            messages.add(resp.toParam());
            messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build());
        }
    }

    private static MessageCreateParams params(Humanoid h, List<MessageParam> messages) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(AssistantConfig.llmModel())
                .maxTokens(4096L)
                .system(String.format(SYSTEM, h.getName()))
                .outputConfig(OutputConfig.builder().effort(effort()).build())
                .messages(messages);
        for (Tools.Tool t : Tools.all()) {
            if (t.name().equals("say")) continue;   // plain text already speaks
            b.addTool(toTool(t));
        }
        return b.build();
    }

    private static OutputConfig.Effort effort() {
        return switch (AssistantConfig.llmEffort()) {
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            default -> OutputConfig.Effort.LOW;
        };
    }

    private static Tool toTool(Tools.Tool t) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        List<String> required = new ArrayList<>();
        for (Tools.Param p : t.params()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", p.type());
            schema.put("description", p.description());
            props.putAdditionalProperty(p.name(), JsonValue.from(schema));
            if (p.required()) required.add(p.name());
        }
        return Tool.builder()
                .name(t.name())
                .description(t.description())
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(required).build())
                .build();
    }

    /** Runs a tool on the server thread and waits (bounded) for its message. */
    private static ContentBlockParam runTool(Humanoid h, CommandSender speaker, ToolUseBlock use) throws Exception {
        JsonObject args = JsonParser.parseString(
                ObjectMappers.jsonMapper().writeValueAsString(use._input())).getAsJsonObject();
        Mai.getInstance().getLogger().info("[LLM] " + h.getName() + " -> " + use.name() + " " + args);

        CompletableFuture<CompletableFuture<String>> started = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(Mai.getInstance(), () -> started.complete(Tools.run(use.name(), h, args, speaker)));
        CompletableFuture<String> job = started.get(5, TimeUnit.SECONDS);

        String content;
        boolean error = false;
        try {
            content = job.get(AssistantConfig.llmToolWaitSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            content = "Started and still in progress (current activity: " + Assistant.of(h).activity()
                    + "). It will keep going; you don't need to wait for it.";
        } catch (java.util.concurrent.ExecutionException e) {
            content = Tools.errorMessage(e);
            error = true;
        }
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(use.id())
                .content(content)
                .isError(error)
                .build());
    }

    private static void speak(Humanoid h, String text, List<String> recent) {
        String line = text.strip();
        if (line.isEmpty()) return;
        synchronized (recent) {
            remember(recent, "<" + h.getName() + "> " + line);
        }
        Bukkit.getScheduler().runTask(Mai.getInstance(), () -> Assistant.of(h).say(line));
    }

    private static void remember(List<String> recent, String line) {
        recent.add(line);
        while (recent.size() > RECENT_LINES) recent.removeFirst();
    }
}
