package com.bsp.admin.module.dict.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 字典枚举数据项（code 为业务表稳定引用键，改名只改 name）
 */
@Data
public class DictItem {

    /** 主键 */
    private Long id;

    /** 所属字典 */
    private Long dictId;

    /** 字典项编码，字典内唯一，如 IN_PROGRESS */
    private String code;

    /** 显示名，如「在建」 */
    private String name;

    /** 扩展字段值，如 {"color":"#3498db","is_terminal":false} */
    private Map<String, Object> ext;

    /** 展示/分组顺序 */
    private Integer sort;

    /** 1=启用 0=停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 1=逻辑删除 */
    private Integer deleted;
}
