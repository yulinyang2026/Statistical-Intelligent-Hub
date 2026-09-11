package com.bsp.admin.module.log;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.log.domain.Log;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志写入（15-系统设置与操作日志.md 的唯一写入入口；展示页留待该模块落地）
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final Repositories repositories;
    private final PermissionService permissionService;

    /** 写一条操作日志；operator 为空时记为「系统」 */
    public void addLog(String module, String target, String action, String detail, String operator) {
        Log log = new Log();
        log.setTime(LocalDateTime.now());
        log.setModule(module);
        log.setTarget(target);
        log.setAction(action);
        log.setDetail(detail);
        log.setOperator(operator == null ? "系统" : operator);
        repositories.getLog().insert(log);
    }

    /** 写一条操作日志，操作人取当前登录用户中文名 */
    public void addLogCurrentUser(String module, String target, String action, String detail) {
        addLog(module, target, action, detail, permissionService.currentUserName());
    }

    /**
     * 系统级操作日志列表（2026-09-11 用户需求）：记录所有人的操作轨迹，仅系统管理员（log:view）可查
     *
     * <p>筛选：操作人/模块/动作模糊、时间区间（起止日期，按天含边界）、关键字（对象或详情），
     * 另支持表头漏斗通用 filters（时间/操作人/模块/对象/动作/详情）。</p>
     */
    public PageResult<Map<String, Object>> list(int current, int size, String operator, String module,
                                                String action, String keyword, String startTime, String endTime,
                                                Map<String, RowFilters.Condition> filters) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "log:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无操作日志查看权限");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rows = repositories.getLog().findAll().stream()
                .filter(l -> operator == null || operator.isBlank()
                        || (l.getOperator() != null && l.getOperator().contains(operator)))
                .filter(l -> module == null || module.isBlank()
                        || (l.getModule() != null && l.getModule().contains(module)))
                .filter(l -> action == null || action.isBlank()
                        || (l.getAction() != null && l.getAction().contains(action)))
                .filter(l -> keyword == null || keyword.isBlank()
                        || (l.getTarget() != null && l.getTarget().contains(keyword))
                        || (l.getDetail() != null && l.getDetail().contains(keyword)))
                .filter(l -> inRange(l.getTime(), startTime, endTime))
                .sorted(Comparator.comparing(Log::getTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", l.getId());
                    row.put("time", l.getTime() == null ? null : l.getTime().format(formatter));
                    row.put("operator", l.getOperator());
                    row.put("module", l.getModule());
                    row.put("target", l.getTarget());
                    row.put("action", l.getAction());
                    row.put("detail", l.getDetail());
                    return row;
                })
                // 表头漏斗通用筛选（2026-09-11）
                .filter(row -> RowFilters.matchRow(row, filters))
                .toList();
        long total = rows.size();
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        return new PageResult<>(rows.subList(from, to), size > 0 ? current : 0, size, total);
    }

    /** 时间区间判定（按天含边界；任一端为空则不限） */
    private boolean inRange(LocalDateTime time, String startTime, String endTime) {
        if (time == null) {
            return false;
        }
        String day = time.toLocalDate().toString();
        if (startTime != null && !startTime.isBlank() && day.compareTo(startTime) < 0) {
            return false;
        }
        return endTime == null || endTime.isBlank() || day.compareTo(endTime) <= 0;
    }

}
