package com.bsp.admin.module.chat;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.module.chat.domain.ChatMessage;
import com.bsp.admin.module.chat.domain.ChatSession;
import com.bsp.admin.module.chat.dto.ChatMessageItem;
import com.bsp.admin.module.chat.dto.ChatSessionItem;
import com.bsp.admin.storage.Repositories;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 管理小帮手服务：会话/消息 JSON 落库 + OpenAI 兼容接口 SSE 流式转发。
 *
 * <p>上游协议：POST {base-url}/chat/completions，stream=true，逐行返回
 * {@code data: {...}}，结束标志 {@code data: [DONE]}；本服务把增量 token
 * 以 {@code data:{"content":"..."}} 转发给前端，消息完成后落库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 系统提示词：助手人设 */
    private static final String SYSTEM_PROMPT =
            "你是「管理小帮手」，统计智能中枢系统的智能客服。"
                    + "请用简洁、专业的中文回答用户关于系统使用、数据统计与分析的问题。";

    /** 会话标题截断长度 */
    private static final int TITLE_MAX_LENGTH = 20;

    private final Repositories repositories;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.base-url}")
    private String baseUrl;

    @Value("${app.ai.api-key}")
    private String apiKey;

    @Value("${app.ai.model}")
    private String model;

    @Value("${app.ai.timeout-seconds:60}")
    private long timeoutSeconds;

    @Value("${app.ai.context-messages:10}")
    private int contextMessages;

    /** 复用连接池（SSE 长连接，每次调用独立 request） */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ==================== 会话 ====================

    /** 当前用户会话列表（最近更新在前） */
    public List<ChatSessionItem> sessions() {
        Long userId = requireUserId();
        return repositories.getChatSession().findAll().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .sorted(Comparator.comparing(ChatSession::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> new ChatSessionItem(s.getId(), s.getTitle(), s.getUpdateTime()))
                .toList();
    }

    /** 新建会话 */
    public Long createSession() {
        Long userId = requireUserId();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("新的对话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        return repositories.getChatSession().insert(session).getId();
    }

    /** 会话消息列表（时间正序） */
    public List<ChatMessageItem> messages(Long sessionId) {
        requireOwnSession(sessionId);
        return repositories.getChatMessage().findAll().stream()
                .filter(m -> sessionId.equals(m.getSessionId()))
                .sorted(Comparator.comparing(ChatMessage::getId))
                .map(m -> new ChatMessageItem(m.getId(), m.getRole(), m.getContent(), m.getCreateTime()))
                .toList();
    }

    // ==================== 流式对话 ====================

    /** 保存用户提问并更新会话标题（流式调用前由 Controller 调用） */
    public void appendUserMessage(Long sessionId, String content) {
        ChatSession session = requireOwnSession(sessionId);
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(session.getUserId());
        message.setRole("user");
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        repositories.getChatMessage().insert(message);

        // 首条消息回填会话标题
        if ("新的对话".equals(session.getTitle())) {
            String title = content.trim().replaceAll("\\s+", " ");
            if (title.length() > TITLE_MAX_LENGTH) {
                title = title.substring(0, TITLE_MAX_LENGTH);
            }
            session.setTitle(title);
        }
        session.setUpdateTime(LocalDateTime.now());
        repositories.getChatSession().updateById(session);
    }

    /**
     * 调用上游模型并流式转发 token（在 StreamingResponseBody 中执行，
     * 响应已提交，异常只能以 SSE error 事件输出，不能抛给全局异常处理器）。
     */
    public void stream(Long sessionId, String content, OutputStream outputStream) {
        if (apiKey == null || apiKey.isBlank()) {
            writeSseError(outputStream, "AI 服务未配置密钥（app.ai.api-key）");
            return;
        }

        StringBuilder answer = new StringBuilder();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(sessionId, content),
                            StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errBody = readBody(response.body());
                log.warn("AI 上游返回 {}：{}", response.statusCode(), errBody);
                writeSseError(outputStream, "AI 服务调用失败（" + response.statusCode() + "）");
                return;
            }

            Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring("data:".length()).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    String token = extractDeltaContent(payload);
                    if (token != null && !token.isEmpty()) {
                        answer.append(token);
                        writer.write("data:" + objectMapper.writeValueAsString(
                                Map.of("content", token)) + "\n\n");
                        writer.flush();
                    }
                }
                writer.write("data:[DONE]\n\n");
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI 上游调用被中断", e);
            writeSseError(outputStream, "AI 服务调用被中断");
        } catch (IOException e) {
            // 客户端断开或上游异常中断：已累积的内容仍落库
            log.warn("AI 流式输出中断", e);
        } catch (Exception e) {
            log.error("AI 对话处理失败", e);
            writeSseError(outputStream, "AI 服务调用失败");
        } finally {
            if (answer.length() > 0) {
                saveAssistantMessage(sessionId, answer.toString());
            }
        }
    }

    /** 组装 OpenAI 兼容 chat.completions 请求体（系统提示 + 最近 N 轮历史 + 本次提问） */
    private String buildRequestBody(Long sessionId, String content) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", true);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);

        List<ChatMessage> history = repositories.getChatMessage().findAll().stream()
                .filter(m -> sessionId.equals(m.getSessionId()))
                .sorted(Comparator.comparing(ChatMessage::getId))
                .toList();
        int from = Math.max(0, history.size() - contextMessages);
        for (ChatMessage message : history.subList(from, history.size())) {
            messages.addObject().put("role", message.getRole()).put("content", message.getContent());
        }
        return body.toString();
    }

    /** 解析上游 SSE 一行 data，提取 choices[0].delta.content 增量 */
    private String extractDeltaContent(String payload) throws IOException {
        JsonNode node = objectMapper.readTree(payload);
        JsonNode choices = node.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode delta = choices.get(0).get("delta");
        if (delta == null) {
            return null;
        }
        JsonNode content = delta.get("content");
        return content == null || content.isNull() ? null : content.asText();
    }

    private void saveAssistantMessage(Long sessionId, String content) {
        ChatSession session = requireOwnSession(sessionId);
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(session.getUserId());
        message.setRole("assistant");
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        repositories.getChatMessage().insert(message);
        session.setUpdateTime(LocalDateTime.now());
        repositories.getChatSession().updateById(session);
    }

    /** 以 SSE 格式输出错误事件（流已开始，不能再用 JSON 响应体） */
    private void writeSseError(OutputStream outputStream, String message) {
        try {
            Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
            writer.write("data:" + objectMapper.writeValueAsString(
                    Map.of("error", message)) + "\n\n");
            writer.write("data:[DONE]\n\n");
            writer.flush();
        } catch (IOException e) {
            log.warn("SSE 错误事件输出失败", e);
        }
    }

    private String readBody(InputStream body) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.length() > 300 ? sb.substring(0, 300) : sb.toString();
        }
    }

    /** 当前用户拥有的会话，否则报「会话不存在」（不泄露他人会话存在性） */
    private ChatSession requireOwnSession(Long sessionId) {
        ChatSession session = repositories.getChatSession().findById(sessionId);
        if (session == null || !requireUserId().equals(session.getUserId())) {
            throw new BizException("会话不存在");
        }
        return session;
    }

    private Long requireUserId() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException("未登录");
        }
        return userId;
    }
}
