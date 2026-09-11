package com.bsp.admin.common.controller;

import com.bsp.admin.common.response.BaseResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 动态菜单接口（仅 backend 路由模式需要，一期 frontend 模式下不调用）
 *
 * <p>结构见技术设计文档 6.5：AppRouteRecord[]，meta.title 为 i18n key，
 * component 为前端 src/views 下的路径映射。</p>
 */
@RestController
@RequiredArgsConstructor
public class MenusController {

    private final ObjectMapper objectMapper;

    @GetMapping(value = "/api/v3/system/menus", produces = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<List<JsonNode>> menus() throws IOException {
        try (InputStream in = new ClassPathResource("seed/menus.json").getInputStream()) {
            JsonNode menus = objectMapper.readTree(in);
            return BaseResponse.ok(objectMapper.convertValue(menus,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class)));
        }
    }
}
