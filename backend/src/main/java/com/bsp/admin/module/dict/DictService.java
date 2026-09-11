package com.bsp.admin.module.dict;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;

import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.dict.domain.Dict;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.dict.domain.DictField;
import com.bsp.admin.module.dict.domain.DictItem;
import com.bsp.admin.module.dict.dto.DictFieldSaveRequest;
import com.bsp.admin.module.dict.dto.DictItemSaveRequest;
import com.bsp.admin.module.dict.dto.DictListItem;
import com.bsp.admin.module.dict.dto.DictSaveRequest;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基础数据（枚举字典）服务：元数据驱动 + JSON 扩展，业务引用用 code。
 */
@Service
@RequiredArgsConstructor
public class DictService {

    /** 字典编码约束：小写蛇形 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** 本期支持的字段类型 */
    private static final List<String> SUPPORTED_TYPES = List.of("string", "enum");

    private final Repositories repositories;
    private final PermissionService permissionService;
    /** 操作日志（2026-09-11 用户需求：所有数据变动均需留痕） */
    private final LogService logService;

    /** 通用列筛选白名单（表头漏斗字段，2026-09-10） */
    private static final Map<String, java.util.function.Function<DictListItem, Object>> DICT_FILTER_FIELDS = Map.ofEntries(
            Map.entry("code", DictListItem::code),
            Map.entry("name", DictListItem::name),
            Map.entry("description", DictListItem::description),
            Map.entry("isBuiltin", DictListItem::isBuiltin),
            Map.entry("status", DictListItem::status),
            Map.entry("sort", DictListItem::sort),
            Map.entry("fieldCount", DictListItem::fieldCount),
            Map.entry("itemCount", DictListItem::itemCount),
            Map.entry("updateTime", DictListItem::updateTime));

    // ==================== 字典定义 ====================

    /** 分页查询（name/code 模糊，status 精确，逻辑删除过滤） */
    public PageResult<DictListItem> page(int current, int size, String name, String code, Integer status,
                                        Map<String, RowFilters.Condition> filters) {
        requireViewPermission();
        List<Dict> dicts = repositories.getDict().findAll().stream()
                .filter(d -> !Integer.valueOf(1).equals(d.getDeleted()))
                .filter(d -> name == null || name.isBlank()
                        || (d.getName() != null && d.getName().contains(name)))
                .filter(d -> code == null || code.isBlank()
                        || (d.getCode() != null && d.getCode().contains(code)))
                .filter(d -> status == null || Objects.equals(d.getStatus(), status))
                .sorted(Comparator.comparing((Dict d) -> d.getSort() == null ? 0 : d.getSort())
                        .thenComparing(Dict::getId))
                .toList();

        Map<Long, Long> fieldCount = repositories.getDictField().findAll().stream()
                .collect(Collectors.groupingBy(DictField::getDictId, Collectors.counting()));
        Map<Long, Long> itemCount = repositories.getDictItem().findAll().stream()
                .filter(i -> !Integer.valueOf(1).equals(i.getDeleted()))
                .filter(i -> Integer.valueOf(1).equals(i.getStatus()))
                .collect(Collectors.groupingBy(DictItem::getDictId, Collectors.counting()));

        List<DictListItem> items = dicts.stream()
                .map(d -> new DictListItem(d.getId(), d.getCode(), d.getName(), d.getDescription(),
                        d.getIsBuiltin(), d.getStatus(), d.getSort(),
                        fieldCount.getOrDefault(d.getId(), 0L),
                        itemCount.getOrDefault(d.getId(), 0L),
                        d.getCreateTime(), d.getUpdateTime()))
                .toList();
        items = items.stream().filter(item -> RowFilters.match(item, filters, DICT_FILTER_FIELDS)).toList();
        return PageResult.of(items, current, size);
    }

    /** 新增 / 修改字典（编码仅新增时可设） */
    public void save(DictSaveRequest request) {
        requireEditPermission();
        if (request.id() == null) {
            create(request);
            logDict("新增字典", request.name(), "编码 " + request.code());
        } else {
            update(request);
            logDict("编辑字典", request.name(), "编码 " + request.code());
        }
    }

    private void create(DictSaveRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("字典编码必须为小写蛇形（小写字母/数字/下划线，字母开头）");
        }
        boolean exists = repositories.getDict().findAll().stream()
                .anyMatch(d -> code.equals(d.getCode()) && !Integer.valueOf(1).equals(d.getDeleted()));
        if (exists) {
            throw new BizException("字典编码已存在：" + code);
        }
        Dict dict = new Dict();
        dict.setCode(code);
        dict.setName(request.name().trim());
        dict.setDescription(request.description());
        dict.setIsBuiltin(0);
        dict.setStatus(1);
        dict.setSort(request.sort() == null ? 0 : request.sort());
        dict.setCreateTime(LocalDateTime.now());
        dict.setUpdateTime(LocalDateTime.now());
        dict.setDeleted(0);
        Dict saved = repositories.getDict().insert(dict);

        // 自动携带系统默认字段 code / name
        createSystemField(saved.getId(), "code", "值编码", 1);
        createSystemField(saved.getId(), "name", "显示名", 2);
    }

    private void createSystemField(Long dictId, String fieldCode, String fieldName, int sort) {
        DictField field = new DictField();
        field.setDictId(dictId);
        field.setFieldCode(fieldCode);
        field.setFieldName(fieldName);
        field.setFieldType("string");
        field.setIsRequired(1);
        field.setIsUnique(0);
        field.setIsSystem(1);
        field.setOptions(null);
        field.setDefaultValue(null);
        field.setSort(sort);
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        field.setUpdateTime(LocalDateTime.now());
        repositories.getDictField().insert(field);
    }

    private void update(DictSaveRequest request) {
        Dict dict = requireDict(request.id());
        dict.setName(request.name().trim());
        dict.setDescription(request.description());
        if (request.sort() != null) {
            dict.setSort(request.sort());
        }
        dict.setUpdateTime(LocalDateTime.now());
        repositories.getDict().updateById(dict);
    }

    /** 启停字典 */
    public void changeStatus(Long id, Integer status) {
        requireEditPermission();
        Dict dict = requireDict(id);
        dict.setStatus(status == null || status != 0 ? 1 : 0);
        dict.setUpdateTime(LocalDateTime.now());
        repositories.getDict().updateById(dict);
        logDict(Integer.valueOf(1).equals(dict.getStatus()) ? "启用字典" : "停用字典", dict.getName(),
                "编码 " + dict.getCode());
    }

    /** 删除字典：内置不可删；存在启用字典项时先停用/删除 */
    public void delete(Long id) {
        requireEditPermission();
        Dict dict = requireDict(id);
        if (Integer.valueOf(1).equals(dict.getIsBuiltin())) {
            throw new BizException("内置字典不允许删除，仅可停用");
        }
        String delName = dict.getName();
        String delCode = dict.getCode();
        boolean hasEnabledItem = repositories.getDictItem().findAll().stream()
                .anyMatch(i -> id.equals(i.getDictId())
                        && !Integer.valueOf(1).equals(i.getDeleted())
                        && Integer.valueOf(1).equals(i.getStatus()));
        if (hasEnabledItem) {
            throw new BizException("请先停用或删除该字典下的全部枚举项");
        }
        dict.setDeleted(1);
        dict.setUpdateTime(LocalDateTime.now());
        repositories.getDict().updateById(dict);
        logDict("删除字典", delName, "编码 " + delCode);
    }

    // ==================== 字典字段 ====================

    /** 字段列表（按 sort 升序，含停用） */
    public List<DictField> fields(Long dictId) {
        requireViewPermission();
        requireDict(dictId);
        return repositories.getDictField().findAll().stream()
                .filter(f -> dictId.equals(f.getDictId()))
                .sorted(Comparator.comparing((DictField f) -> f.getSort() == null ? 0 : f.getSort())
                        .thenComparing(DictField::getId))
                .toList();
    }

    /** 新增 / 修改字段（字段编码不可改） */
    public void saveField(DictFieldSaveRequest request) {
        requireEditPermission();
        requireDict(request.dictId());
        if (!SUPPORTED_TYPES.contains(request.fieldType())) {
            throw new BizException("本期仅支持字段类型：string / enum");
        }
        if (request.id() == null) {
            createField(request);
            logDict("新增字典字段", request.fieldName(),
                    "字段编码 " + request.fieldCode() + " / 类型 " + request.fieldType() + " / 字典 id " + request.dictId());
        } else {
            updateField(request);
            logDict("编辑字典字段", request.fieldName(),
                    "字段编码 " + request.fieldCode() + " / 类型 " + request.fieldType() + " / 字典 id " + request.dictId());
        }
    }

    private void createField(DictFieldSaveRequest request) {
        String fieldCode = request.fieldCode().trim().toLowerCase(Locale.ROOT);
        boolean exists = repositories.getDictField().findAll().stream()
                .anyMatch(f -> request.dictId().equals(f.getDictId()) && fieldCode.equals(f.getFieldCode()));
        if (exists) {
            throw new BizException("字段编码已存在：" + fieldCode);
        }
        if ("enum".equals(request.fieldType())
                && (request.options() == null || request.options().isEmpty())) {
            throw new BizException("enum 类型字段必须填写可选项");
        }
        DictField field = new DictField();
        field.setDictId(request.dictId());
        field.setFieldCode(fieldCode);
        field.setFieldName(request.fieldName().trim());
        field.setFieldType(request.fieldType());
        field.setIsRequired(request.isRequired() == null ? 0 : request.isRequired());
        field.setIsUnique(request.isUnique() == null ? 0 : request.isUnique());
        field.setIsSystem(0);
        field.setOptions(request.options());
        field.setDefaultValue(request.defaultValue());
        field.setSort(request.sort() == null ? 99 : request.sort());
        field.setStatus(1);
        field.setCreateTime(LocalDateTime.now());
        field.setUpdateTime(LocalDateTime.now());
        repositories.getDictField().insert(field);
    }

    private void updateField(DictFieldSaveRequest request) {
        DictField field = repositories.getDictField().findById(request.id());
        if (field == null) {
            throw new BizException("字段不存在");
        }
        if ("enum".equals(request.fieldType())
                && (request.options() == null || request.options().isEmpty())) {
            throw new BizException("enum 类型字段必须填写可选项");
        }
        field.setFieldName(request.fieldName().trim());
        field.setFieldType(request.fieldType());
        field.setIsRequired(request.isRequired() == null ? 0 : request.isRequired());
        field.setIsUnique(request.isUnique() == null ? 0 : request.isUnique());
        field.setOptions(request.options());
        field.setDefaultValue(request.defaultValue());
        if (request.sort() != null) {
            field.setSort(request.sort());
        }
        field.setUpdateTime(LocalDateTime.now());
        repositories.getDictField().updateById(field);
    }

    /** 停用 / 启用字段（停用后表单不再展示、校验跳过，保留历史值） */
    public void changeFieldStatus(Long id, Integer status) {
        requireEditPermission();
        DictField field = repositories.getDictField().findById(id);
        if (field == null) {
            throw new BizException("字段不存在");
        }
        field.setStatus(status == null || status != 0 ? 1 : 0);
        field.setUpdateTime(LocalDateTime.now());
        repositories.getDictField().updateById(field);
        logDict(Integer.valueOf(1).equals(field.getStatus()) ? "启用字典字段" : "停用字典字段",
                field.getFieldName(), "字段编码 " + field.getFieldCode() + " / 字典 id " + field.getDictId());
    }

    // ==================== 字典枚举项 ====================

    /** 枚举项列表（按 sort 升序；kw 模糊匹配 code/name） */
    public List<DictItem> items(Long dictId, String kw) {
        requireViewPermission();
        requireDict(dictId);
        return repositories.getDictItem().findAll().stream()
                .filter(i -> dictId.equals(i.getDictId()))
                .filter(i -> !Integer.valueOf(1).equals(i.getDeleted()))
                .filter(i -> kw == null || kw.isBlank()
                        || (i.getCode() != null && i.getCode().contains(kw))
                        || (i.getName() != null && i.getName().contains(kw)))
                .sorted(Comparator.comparing((DictItem i) -> i.getSort() == null ? 0 : i.getSort())
                        .thenComparing(DictItem::getId))
                .toList();
    }

    /** 新增 / 修改枚举项（code 不可改；按字段定义校验 ext） */
    public void saveItem(DictItemSaveRequest request) {
        requireEditPermission();
        requireDict(request.dictId());
        if (request.id() == null) {
            createItem(request);
            logDict("新增字典项", request.name(), "编码 " + request.code() + " / 字典 id " + request.dictId());
        } else {
            updateItem(request);
            logDict("编辑字典项", request.name(), "编码 " + request.code() + " / 字典 id " + request.dictId());
        }
    }

    private void createItem(DictItemSaveRequest request) {
        String code = request.code().trim();
        boolean exists = repositories.getDictItem().findAll().stream()
                .anyMatch(i -> request.dictId().equals(i.getDictId())
                        && code.equals(i.getCode())
                        && !Integer.valueOf(1).equals(i.getDeleted()));
        if (exists) {
            throw new BizException("字典项编码已存在：" + code);
        }
        DictItem item = new DictItem();
        item.setDictId(request.dictId());
        item.setCode(code);
        item.setName(request.name().trim());
        item.setExt(validateAndNormalizeExt(request.dictId(), null, request.ext()));
        item.setSort(request.sort() == null ? maxSort(request.dictId()) + 1 : request.sort());
        item.setStatus(1);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        item.setDeleted(0);
        repositories.getDictItem().insert(item);
    }

    private void updateItem(DictItemSaveRequest request) {
        DictItem item = repositories.getDictItem().findById(request.id());
        if (item == null) {
            throw new BizException("字典项不存在");
        }
        // 先校验、后改实体：findById 返回的是仓储内存镜像的同一个对象，
        // 若先 setName 再校验、校验抛错，脏值会留在内存里并被**后续任意一次写盘**带下去
        // （2026-09-11 实测踩到：ext 校验失败返回 400，但显示名已被改掉）
        Map<String, Object> ext = validateAndNormalizeExt(item.getDictId(), item.getId(), request.ext());
        item.setName(request.name().trim());
        item.setExt(ext);
        if (request.sort() != null) {
            item.setSort(request.sort());
        }
        item.setUpdateTime(LocalDateTime.now());
        repositories.getDictItem().updateById(item);
    }

    /**
     * 按 dict_field 校验扩展字段：必填 / enum 取值 / 字典内唯一；
     * 返回规整后的 ext（保留未定义键，仅校验已定义字段）。
     */
    private Map<String, Object> validateAndNormalizeExt(Long dictId, Long excludeItemId, Map<String, Object> ext) {
        Map<String, Object> result = ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext);
        List<DictField> fields = repositories.getDictField().findAll().stream()
                .filter(f -> dictId.equals(f.getDictId()))
                .filter(f -> Integer.valueOf(1).equals(f.getStatus()))
                .filter(f -> !Integer.valueOf(1).equals(f.getIsSystem()))
                .toList();

        for (DictField field : fields) {
            Object value = result.get(field.getFieldCode());
            boolean required = Integer.valueOf(1).equals(field.getIsRequired());
            if (required && (value == null || String.valueOf(value).isBlank())) {
                throw new BizException("字段「" + field.getFieldName() + "」为必填");
            }
            if (value == null) {
                continue;
            }
            if ("enum".equals(field.getFieldType())) {
                String str = String.valueOf(value);
                if (field.getOptions() == null || !field.getOptions().contains(str)) {
                    throw new BizException("字段「" + field.getFieldName() + "」取值必须在可选项内");
                }
                result.put(field.getFieldCode(), str);
            } else {
                result.put(field.getFieldCode(), String.valueOf(value));
            }
            if (Integer.valueOf(1).equals(field.getIsUnique())) {
                String finalValue = String.valueOf(result.get(field.getFieldCode()));
                boolean duplicated = repositories.getDictItem().findAll().stream()
                        .filter(i -> dictId.equals(i.getDictId()))
                        .filter(i -> !Integer.valueOf(1).equals(i.getDeleted()))
                        .filter(i -> !Objects.equals(i.getId(), excludeItemId))
                        .anyMatch(i -> {
                            Map<String, Object> other = i.getExt();
                            return other != null && finalValue.equals(String.valueOf(other.get(field.getFieldCode())));
                        });
                if (duplicated) {
                    throw new BizException("字段「" + field.getFieldName() + "」要求唯一，该值已被占用");
                }
            }
        }
        return result;
    }

    /** 启停枚举项 */
    public void changeItemStatus(Long id, Integer status) {
        requireEditPermission();
        DictItem item = repositories.getDictItem().findById(id);
        if (item == null) {
            throw new BizException("字典项不存在");
        }
        item.setStatus(status == null || status != 0 ? 1 : 0);
        item.setUpdateTime(LocalDateTime.now());
        repositories.getDictItem().updateById(item);
        logDict(Integer.valueOf(1).equals(item.getStatus()) ? "启用字典项" : "停用字典项",
                item.getName(), "编码 " + item.getCode() + " / 字典 id " + item.getDictId());
    }

    /** 上移 / 下移（与相邻项交换 sort） */
    public void moveItem(Long id, String direction) {
        requireEditPermission();
        DictItem item = repositories.getDictItem().findById(id);
        if (item == null) {
            throw new BizException("字典项不存在");
        }
        List<DictItem> siblings = items(item.getDictId(), null);
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (Objects.equals(siblings.get(i).getId(), id)) {
                index = i;
                break;
            }
        }
        int target = "up".equals(direction) ? index - 1 : index + 1;
        if (index < 0 || target < 0 || target >= siblings.size()) {
            return;
        }
        DictItem other = siblings.get(target);
        int tmp = item.getSort() == null ? 0 : item.getSort();
        item.setSort(other.getSort() == null ? 0 : other.getSort());
        other.setSort(tmp);
        item.setUpdateTime(LocalDateTime.now());
        other.setUpdateTime(LocalDateTime.now());
        repositories.getDictItem().updateById(item);
        repositories.getDictItem().updateById(other);
        logDict("up".equals(direction) ? "枚举项上移" : "枚举项下移", item.getName(),
                "挪动顺序 " + index + " → " + target + "（字典 id " + item.getDictId() + "）");
    }

    /**
     * 拖动排序（2026-09-11 用户需求）：按传入 id 顺序把 sort 重写为 1..n。
     *
     * <p>传入集合须与该字典当前枚举项**完全一致**（数量与内容都相等），避免漏传/多传导致顺序残缺；
     * 关键词过滤下前端不启用拖动，故正常调用总是全量列表。</p>
     */
    public void reorderItems(Long dictId, List<Long> ids) {
        requireEditPermission();
        Dict dict = requireDict(dictId);
        List<DictItem> current = items(dictId, null);
        Set<Long> currentIds = current.stream().map(DictItem::getId).collect(Collectors.toSet());
        if (ids == null || ids.size() != current.size() || !currentIds.equals(new LinkedHashSet<>(ids))) {
            throw new BizException("排序列表与字典项不一致，请刷新后重试");
        }
        for (int i = 0; i < ids.size(); i++) {
            DictItem item = repositories.getDictItem().findById(ids.get(i));
            item.setSort(i + 1);
            item.setUpdateTime(LocalDateTime.now());
            repositories.getDictItem().updateById(item);
        }
        logDict("枚举项拖动排序", dict.getName(),
                "新顺序：" + current.stream().map(DictItem::getName).collect(Collectors.joining("、")));
    }

    /** 逻辑删除枚举项（业务引用由维护人员人工确认） */
    public void deleteItem(Long id) {
        requireEditPermission();
        DictItem item = repositories.getDictItem().findById(id);
        if (item == null) {
            throw new BizException("字典项不存在");
        }
        item.setDeleted(1);
        item.setUpdateTime(LocalDateTime.now());
        repositories.getDictItem().updateById(item);
        logDict("删除字典项", item.getName(), "编码 " + item.getCode() + " / 字典 id " + item.getDictId());
    }

    /** 业务下拉：按字典 code 返回启用枚举项（含 ext，供状态色块等真实渲染） */
    public List<DictItem> options(String dictCode) {
        return repositories.getDict().findAll().stream()
                .filter(d -> dictCode.equals(d.getCode()))
                .filter(d -> Integer.valueOf(1).equals(d.getStatus()))
                .findFirst()
                .map(dict -> repositories.getDictItem().findAll().stream()
                        .filter(i -> dict.getId().equals(i.getDictId()))
                        .filter(i -> !Integer.valueOf(1).equals(i.getDeleted()))
                        .filter(i -> Integer.valueOf(1).equals(i.getStatus()))
                        .sorted(Comparator.comparing((DictItem i) -> i.getSort() == null ? 0 : i.getSort())
                                .thenComparing(DictItem::getId))
                        .toList())
                .orElseThrow(() -> new BizException("字典不存在或已停用：" + dictCode));
    }

    // ==================== 内部工具 ====================

    private Dict requireDict(Long id) {
        Dict dict = repositories.getDict().findById(id);
        if (dict == null || Integer.valueOf(1).equals(dict.getDeleted())) {
            throw new BizException("字典不存在");
        }
        return dict;
    }

    private int maxSort(Long dictId) {
        return repositories.getDictItem().findAll().stream()
                .filter(i -> dictId.equals(i.getDictId()))
                .mapToInt(i -> i.getSort() == null ? 0 : i.getSort())
                .max()
                .orElse(0);
    }

    /** 判权：基础数据管理页读取（dict:view，2026-09-10 新增权限点） */
    private void requireViewPermission() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "dict:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无基础数据查看权限");
        }
    }

    /** 判权：基础数据增删改（dict:edit） */
    private void requireEditPermission() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "dict:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无基础数据编辑权限");
        }
    }

    /** 判权：系统管理类接口按权限点校验（2026-09-10 权限审计修复） */
    private void requirePerm(String code) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
    }


    /** 基础数据日志：对象格式 名称，与其他模块一致 */
    private void logDict(String action, String target, String detail) {
        logService.addLogCurrentUser("dict", target, action, detail);
    }
}