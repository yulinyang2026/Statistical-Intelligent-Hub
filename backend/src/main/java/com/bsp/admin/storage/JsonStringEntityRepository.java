package com.bsp.admin.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * JSON 文件仓储（字符串主键版本，供任务/归属数据源等 string-id 实体使用）
 *
 * <p>与 {@link JsonEntityRepository} 的差异：实体 id 为字符串且由调用方显式赋值
 * （如 task1、p1），insert 不自动生成主键；其余原子写、读写锁、seed 语义一致。</p>
 *
 * @param <T> 实体类型（必须有 String 型 id 字段，通过 getter/setter 函数注入）
 */
@Slf4j
public class JsonStringEntityRepository<T> {

    private final ObjectMapper objectMapper;
    private final Path file;
    private final TypeReference<List<T>> listType;
    private final Function<T, String> idGetter;
    private final BiConsumer<T, String> idSetter;

    /** 内存镜像（读写锁保护） */
    private final List<T> data = new ArrayList<>();

    /** 启动时数据文件是否已存在（决定是否触发 seed 导入） */
    private boolean loadedFromFile;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public JsonStringEntityRepository(ObjectMapper objectMapper, Path file, TypeReference<List<T>> listType,
                                      Function<T, String> idGetter, BiConsumer<T, String> idSetter) {
        this.objectMapper = objectMapper;
        this.file = file;
        this.listType = listType;
        this.idGetter = idGetter;
        this.idSetter = idSetter;
        load();
    }

    /** 启动加载：文件存在则读取；不存在则空仓（seed 由 Repositories 负责） */
    private void load() {
        this.loadedFromFile = Files.exists(file);
        try {
            if (loadedFromFile) {
                List<T> loaded = objectMapper.readValue(file.toFile(), listType);
                data.addAll(loaded);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("数据文件加载失败: " + file, e);
        }
        log.info("已加载实体 {}：{} 条，文件 {}", file.getFileName(), data.size(), file.toAbsolutePath());
    }

    /** 启动时数据文件是否已存在（Repositories 据此判断是否需要 seed） */
    boolean loadedFromFile() {
        return loadedFromFile;
    }

    public T findById(String id) {
        lock.readLock().lock();
        try {
            return data.stream().filter(t -> id.equals(idGetter.apply(t))).findFirst().orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<T> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public T insert(T entity) {
        lock.writeLock().lock();
        try {
            data.add(entity);
            persist();
            return entity;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public T updateById(T entity) {
        lock.writeLock().lock();
        try {
            String id = idGetter.apply(entity);
            for (int i = 0; i < data.size(); i++) {
                if (id.equals(idGetter.apply(data.get(i)))) {
                    data.set(i, entity);
                    persist();
                    return entity;
                }
            }
            throw new com.bsp.admin.common.exception.BizException("记录不存在: id=" + id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void deleteById(String id) {
        lock.writeLock().lock();
        try {
            boolean removed = data.removeIf(t -> id.equals(idGetter.apply(t)));
            if (removed) {
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 删除满足条件的全部记录 */
    public int deleteIf(Predicate<T> predicate) {
        lock.writeLock().lock();
        try {
            int before = data.size();
            data.removeIf(predicate);
            int removed = before - data.size();
            if (removed > 0) {
                persist();
            }
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 原子落盘：先写 .tmp 再 move，进程崩溃最多丢最后一次写 */
    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(tmp.toFile(), new ArrayList<>(data));
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("数据文件落盘失败: " + file, e);
        }
    }

    /** 首次启动导入 seed（仅当数据文件不存在时由 Repositories 调用） */
    void seedAll(List<T> seeds) {
        lock.writeLock().lock();
        try {
            if (!data.isEmpty()) {
                return;
            }
            data.addAll(seeds);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
