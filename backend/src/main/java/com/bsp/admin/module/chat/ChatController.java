package com.bsp.admin.module.chat;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.chat.dto.ChatMessageItem;
import com.bsp.admin.module.chat.dto.ChatSessionItem;
import com.bsp.admin.module.chat.dto.ChatStreamRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 管理小帮手（AI 对话）接口
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 当前用户会话列表（最近更新在前） */
    @GetMapping("/sessions")
    public BaseResponse<List<ChatSessionItem>> sessions() {
        return BaseResponse.ok(chatService.sessions());
    }

    /** 新建会话，返回会话 id */
    @PostMapping("/sessions")
    public BaseResponse<Long> createSession() {
        return BaseResponse.ok(chatService.createSession());
    }

    /** 会话消息列表（时间正序） */
    @GetMapping("/sessions/{id}/messages")
    public BaseResponse<List<ChatMessageItem>> messages(@PathVariable Long id) {
        return BaseResponse.ok(chatService.messages(id));
    }

    /**
     * 流式对话（SSE）：先落库用户提问，再把上游 token 以
     * {@code data:{"content":"..."}} 逐段转发，结束标志 {@code data:[DONE]}。
     */
    @PostMapping(value = "/sessions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable Long id,
                                                        @Valid @RequestBody ChatStreamRequest request) {
        // 保存提问并校验会话归属（在响应提交前抛错仍走统一 JSON 错误体）
        chatService.appendUserMessage(id, request.content());

        StreamingResponseBody body =
                outputStream -> chatService.stream(id, request.content(), outputStream);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8))
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }
}
