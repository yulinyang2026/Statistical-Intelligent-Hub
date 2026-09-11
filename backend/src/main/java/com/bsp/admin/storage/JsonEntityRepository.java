package com.bsp.admin.storage;

import com.bsp.admin.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

/**
 * JSON 文件仓储（一期实现）
 *
 * <ul>
 *   <li>启动时整体加载进内存镜像，增删改更新镜像后整体序列化落盘（写穿）</li>
 *   <li>原子写：先写 &lt;file&gt;.tmp 再 Files.move(ATOMIC_MOVE)，避免半截文件</li>
 *   <li>每实体一把 ReentrantReadWriteLock，写锁覆盖「改内存 + 落盘」全过程</li>
 *   <li>ID 生成：AtomicLong，启动时以文件内 max(id) 初始化</li>
 *   <li>字段演进：反序列化 fail-on-unknown-properties = false，缺省字段兜底</li>
 * </ul>
 *
 * @param <T> 实体类型（必须有 Long 型 id 字段，通过 getter/setter 函数注入）
 */
@Slf4j
public class JsonEntityRepository<T> implements EntityRepository<T> {

    private final ObjectMapper objectMapper;
    private final Path file;
    private final TypeReference<List<T>> listType;
    private final ToLongFunction<T> idGetter;
    private final BiConsumer<T, Long> idSetter;

    /** 内存镜像（读写锁保护） */
    private final List<T> data = new ArrayList<>();

    /** 启动时数据文件是否已存在（决定是否触发 seed 导入） */
    private boolean loadedFromFile;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public JsonEntityRepository(ObjectMapper objectMapper, Path file, TypeReference<List<T>> listType,
                                ToLongFunction<T> idGetter, BiConsumer<T, Long> idSetter) {
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
        data.stream().mapToLong(idGetter).max().ifPresent(max -> idGenerator.set(max));
        log.info("已加载实体 {}：{} 条，文件 {}", file.getFileName(), data.size(), file.toAbsolutePath());
    }

    /** 启动时数据文件是否已存在（Repositories 据此判断是否需要 seed） */
    boolean loadedFromFile() {
        return loadedFromFile;
    }

    @Override
    public T findById(Long id) {
        lock.readLock().lock();
        try {
            return data.stream().filter(t -> idGetter.applyAsLong(t) == id).findFirst().orElse(null);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<T> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> findByCondition(Map<String, Object> condition, int current, int size) {
        List<T> all = findAll();
        List<T> filtered = all.stream()
                .filter(t -> {
                    Map<String, Object> fields =
                            objectMapper.convertValue(t, new TypeReference<Map<String, Object>>() {});
                    return condition.entrySet().stream().allMatch(entry -> {
                        Object actual = fields.get(entry.getKey());
                        Object expected = entry.getValue();
                        return actual != null && String.valueOf(actual).equals(String.valueOf(expected));
                    });
                })
                .sorted(Comparator.comparingLong(idGetter).reversed())
                .toList();
        int from = (int) Math.min((long) (current - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return filtered.subList(from, to);
    }

    @Override
    public T insert(T entity) {
        lock.writeLock().lock();
        try {
            idSetter.accept(entity, idGenerator.incrementAndGet());
            data.add(entity);
            persist();
            return entity;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public T updateById(T entity) {
        lock.writeLock().lock();
        try {
            long id = idGetter.applyAsLong(entity);
            for (int i = 0; i < data.size(); i++) {
                if (idGetter.applyAsLong(data.get(i)) == id) {
                    data.set(i, entity);
                    persist();
                    return entity;
                }
            }
            throw new BizException("记录不存在: id=" + id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteById(Long id) {
        lock.writeLock().lock();
        try {
            boolean removed = data.removeIf(t -> idGetter.applyAsLong(t) == id);
            if (removed) {
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 删除满足条件的全部记录（关联表清理用） */
    public int deleteIf(java.util.function.Predicate<T> predicate) {
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
            data.stream().mapToLong(idGetter).max().ifPresent(max -> idGenerator.set(max));
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
