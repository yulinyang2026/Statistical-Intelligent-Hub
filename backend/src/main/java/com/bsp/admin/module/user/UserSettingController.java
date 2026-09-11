package com.bsp.admin.module.user;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.user.domain.UserSetting;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户级界面设置（2026-09-11 用户需求：系统设置按用户存库，每人可自定义自己的风格）。
 *
 * <p>只读写**当前登录用户自己**的设置，无需额外权限点；内容为前端 setting store 的状态子集
 * （主题风格 / 菜单布局 / 菜单风格 / 系统主题色 / 基础配置等），原样 JSON 存取。</p>
 */
@RestController
@RequestMapping("/api/user/settings")
@RequiredArgsConstructor
public class UserSettingController {

    private final Repositories repositories;

    /** 读取本人设置（未保存过返回空对象） */
    @GetMapping
    public BaseResponse<Map<String, Object>> get() {
        UserSetting setting = findCurrent();
        Map<String, Object> payload = setting == null ? null : setting.getPayload();
        return BaseResponse.ok(payload == null ? Map.of() : payload);
    }

    /** 保存本人设置（整份覆盖） */
    @PutMapping
    public BaseResponse<Void> save(@RequestBody Map<String, Object> payload) {
        Long userId = AuthContext.getUserId();
        UserSetting setting = findCurrent();
        if (setting == null) {
            setting = new UserSetting();
            setting.setUserId(userId);
            setting.setPayload(payload == null ? Map.of() : payload);
            setting.setUpdateTime(LocalDateTime.now());
            repositories.getUserSetting().insert(setting);
        } else {
            setting.setPayload(payload == null ? Map.of() : payload);
            setting.setUpdateTime(LocalDateTime.now());
            repositories.getUserSetting().updateById(setting);
        }
        return BaseResponse.ok();
    }

    private UserSetting findCurrent() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return null;
        }
        List<UserSetting> all = repositories.getUserSetting().findAll().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .toList();
        return all.isEmpty() ? null : all.get(0);
    }
}
