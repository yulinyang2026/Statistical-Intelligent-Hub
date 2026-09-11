package com.bsp.admin.module.dict.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典字段定义（字典项的扩展属性结构；编码不可改）
 */
@Data
public class DictField {

    /** 主键 */
    private Long id;

    /** 所属字典 */
    private Long dictId;

    /** 字段编码，如 color */
    private String fieldCode;

    /** 字段显示名 */
    private String fieldName;

    /** 字段类型：string/int/date/enum/bool（本期仅 string/enum） */
    private String fieldType;

    /** 1=必填 */
    private Integer isRequired;

    /** 1=字典内唯一 */
    private Integer isUnique;

    /** 1=系统默认字段（code/name），不可删改编码 */
    private Integer isSystem;

    /** enum 类型的可选项 */
    private List<String> options;

    /** 默认值（老数据兜底展示） */
    private String defaultValue;

    /** 字段顺序 */
    private Integer sort;

    /** 1=启用 0=停用（停用后表单不再展示、校验跳过） */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
