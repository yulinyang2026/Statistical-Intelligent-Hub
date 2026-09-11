package com.bsp.admin.module.topic;

import com.bsp.admin.common.excel.ExcelUtil;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.BaseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 专题 Excel 导入导出（FR-TOP-006，规则同模块管理）。
 *
 * <p><b>2026-09-11 用户需求</b>：由 CSV 改为 **Excel（.xlsx）**，不再支持 CSV（读写基于 Apache POI）。</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TopicTransferController {

    private final TopicService topicService;

    /** 导出当前筛选结果全量（GET 便于浏览器直开；token 走 ?token= 参数） */
    @GetMapping({"/export/topics", "/export/topic"})
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String kw,
            @RequestParam(required = false) List<String> dept,
            @RequestParam(required = false) List<String> team,
            @RequestParam(required = false) List<String> owner,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String filters) {
        byte[] body = ExcelUtil.write("专题",
                topicService.excelHeaders(),
                topicService.excelRows(kw, dept, team, owner, status, scope, RowFilters.parse(filters)));
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType(ExcelUtil.XLSX_TYPE));
        httpHeaders.setContentDisposition(ContentDisposition.attachment()
                .filename("topics.xlsx", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(httpHeaders).body(body);
    }

    /** 导入 Excel（multipart；按表头映射，非法字典值忽略） */
    @PostMapping({"/import/topics", "/import/topic"})
    public BaseResponse<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file)
            throws Exception {
        return BaseResponse.ok(topicService.importRows(ExcelUtil.readAsMaps(file.getInputStream())));
    }
}
