package com.bsp.admin.module.dict;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.dict.domain.DictField;
import com.bsp.admin.module.dict.domain.DictItem;
import com.bsp.admin.module.dict.dto.DictFieldSaveRequest;
import com.bsp.admin.module.dict.dto.DictItemReorderRequest;
import com.bsp.admin.module.dict.dto.DictItemSaveRequest;
import com.bsp.admin.module.dict.dto.DictListItem;
import com.bsp.admin.module.dict.dto.DictSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础数据（枚举字典）接口
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /** 字典分页（name/code 模糊，status 精确） */
    @GetMapping("/page")
    public BaseResponse<PageResult<DictListItem>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(dictService.page(current, size, name, code, status, com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 业务下拉：按字典 code 取启用枚举项（含 ext） */
    @GetMapping("/options")
    public BaseResponse<List<DictItem>> options(@RequestParam String code) {
        return BaseResponse.ok(dictService.options(code));
    }

    /** 新增字典 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody DictSaveRequest request) {
        dictService.save(request);
        return BaseResponse.ok();
    }

    /** 修改字典（编码不可改） */
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody DictSaveRequest request) {
        dictService.save(request);
        return BaseResponse.ok();
    }

    /** 启停字典 */
    @PutMapping("/{id}/status")
    public BaseResponse<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        dictService.changeStatus(id, status);
        return BaseResponse.ok();
    }

    /** 删除字典（内置不可删） */
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return BaseResponse.ok();
    }

    /** 字段列表 */
    @GetMapping("/{id}/fields")
    public BaseResponse<List<DictField>> fields(@PathVariable Long id) {
        return BaseResponse.ok(dictService.fields(id));
    }

    /** 新增字段 */
    @PostMapping("/field")
    public BaseResponse<Void> createField(@Valid @RequestBody DictFieldSaveRequest request) {
        dictService.saveField(request);
        return BaseResponse.ok();
    }

    /** 修改字段（字段编码不可改） */
    @PutMapping("/field")
    public BaseResponse<Void> updateField(@Valid @RequestBody DictFieldSaveRequest request) {
        dictService.saveField(request);
        return BaseResponse.ok();
    }

    /** 启停字段 */
    @PutMapping("/field/{id}/status")
    public BaseResponse<Void> changeFieldStatus(@PathVariable Long id, @RequestParam Integer status) {
        dictService.changeFieldStatus(id, status);
        return BaseResponse.ok();
    }

    /** 枚举项列表（kw 模糊 code/name） */
    @GetMapping("/{id}/items")
    public BaseResponse<List<DictItem>> items(@PathVariable Long id,
                                              @RequestParam(required = false) String kw) {
        return BaseResponse.ok(dictService.items(id, kw));
    }

    /** 新增枚举项 */
    @PostMapping("/item")
    public BaseResponse<Void> createItem(@Valid @RequestBody DictItemSaveRequest request) {
        dictService.saveItem(request);
        return BaseResponse.ok();
    }

    /** 修改枚举项（code 不可改） */
    @PutMapping("/item")
    public BaseResponse<Void> updateItem(@Valid @RequestBody DictItemSaveRequest request) {
        dictService.saveItem(request);
        return BaseResponse.ok();
    }

    /** 启停枚举项 */
    @PutMapping("/item/{id}/status")
    public BaseResponse<Void> changeItemStatus(@PathVariable Long id, @RequestParam Integer status) {
        dictService.changeItemStatus(id, status);
        return BaseResponse.ok();
    }

    /** 枚举项上移 / 下移（界面已改为拖动排序，接口保留兼容） */
    @PutMapping("/item/{id}/move")
    public BaseResponse<Void> moveItem(@PathVariable Long id, @RequestParam String direction) {
        dictService.moveItem(id, direction);
        return BaseResponse.ok();
    }

    /** 枚举项拖动排序（2026-09-11 用户需求：按传入 id 顺序重写 sort） */
    @PostMapping("/{id}/items/reorder")
    public BaseResponse<Void> reorderItems(@PathVariable Long id,
                                           @Valid @RequestBody DictItemReorderRequest request) {
        dictService.reorderItems(id, request.ids());
        return BaseResponse.ok();
    }

    /** 逻辑删除枚举项 */
    @DeleteMapping("/item/{id}")
    public BaseResponse<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return BaseResponse.ok();
    }
}
