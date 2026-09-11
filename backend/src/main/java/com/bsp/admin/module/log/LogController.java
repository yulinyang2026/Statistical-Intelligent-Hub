package com.bsp.admin.module.log;

import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统级操作日志接口（2026-09-11 用户需求）：记录所有人的操作轨迹，仅系统管理员（log:view）可查
 */
@RestController
@RequestMapping("/api/system/log")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /** 操作日志列表（分页 + 操作人/模块/动作/关键字/时间区间 + 表头漏斗 filters） */
    @GetMapping("/page")
    public BaseResponse<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(logService.list(current, size, operator, module, action, keyword,
                startTime, endTime, RowFilters.parse(filters)));
    }
}
