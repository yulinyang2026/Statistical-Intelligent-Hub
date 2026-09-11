package com.bsp.admin.storage;

import com.bsp.admin.module.belong.domain.BelongProject;
import com.bsp.admin.module.belong.domain.BelongSubsystem;
import com.bsp.admin.module.belong.domain.BelongTopic;
import com.bsp.admin.module.chat.domain.ChatMessage;
import com.bsp.admin.module.chat.domain.ChatSession;
import com.bsp.admin.module.dict.domain.Dict;
import com.bsp.admin.module.dict.domain.DictField;
import com.bsp.admin.module.dict.domain.DictItem;
import com.bsp.admin.module.doc.domain.Doc;
import com.bsp.admin.module.log.domain.Log;
import com.bsp.admin.module.menu.domain.MenuOrder;
import com.bsp.admin.module.mgmt.domain.Module;
import com.bsp.admin.module.org.domain.Org;
import com.bsp.admin.module.project.domain.Project;
import com.bsp.admin.module.project.domain.Subsystem;
import com.bsp.admin.module.task.domain.Task;
import com.bsp.admin.module.topic.domain.Topic;
import com.bsp.admin.module.role.domain.Permission;
import com.bsp.admin.module.role.domain.Role;
import com.bsp.admin.module.role.domain.RolePermission;
import com.bsp.admin.module.user.domain.Account;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.module.user.domain.UserOrg;
import com.bsp.admin.module.user.domain.UserRole;
import com.bsp.admin.module.user.domain.UserSetting;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 全部实体仓储的聚合入口（Spring 单例，Service 层只依赖本类的仓储字段）
 *
 * <p>首次启动（数据文件不存在）自动导入 classpath:seed/*.json 初始数据；
 * seed 账号口令使用 "SEED:明文" 占位，导入时替换为 BCrypt 哈希。</p>
 */
@Getter
@Component
public class Repositories {

    public final JsonEntityRepository<Org> org;
    public final JsonEntityRepository<User> user;
    public final JsonEntityRepository<Account> account;
    public final JsonEntityRepository<UserOrg> userOrg;
    public final JsonEntityRepository<UserRole> userRole;
    /** 用户级界面设置（2026-09-11 用户需求：系统设置按用户存库） */
    public final JsonEntityRepository<UserSetting> userSetting;
    public final JsonEntityRepository<Role> role;
    public final JsonEntityRepository<Permission> permission;
    public final JsonEntityRepository<RolePermission> rolePermission;
    public final JsonEntityRepository<Dict> dict;
    public final JsonEntityRepository<DictField> dictField;
    public final JsonEntityRepository<DictItem> dictItem;
    public final JsonStringEntityRepository<Task> task;
    public final JsonStringEntityRepository<BelongProject> belongProject;
    public final JsonStringEntityRepository<BelongTopic> belongTopic;
    public final JsonStringEntityRepository<BelongSubsystem> belongSubsystem;
    /** 功能模块（05-模块管理正式实体，替换原 belong 垫底数据源） */
    public final JsonStringEntityRepository<Module> module;
    /** 专题（06-专题管理正式实体，替换原 BelongTopic 垫底数据源） */
    public final JsonStringEntityRepository<Topic> topic;
    /** 项目（07-项目管理正式实体，替换原 belong-project 垫底数据源；存量 p1/p2 按原 id 迁移） */
    public final JsonStringEntityRepository<Project> project;
    /** 子系统（07-项目下级实体，替换原 belong-subsystem 垫底数据源；存量 s1/s2 按原 id 迁移） */
    public final JsonStringEntityRepository<Subsystem> subsystem;
    /** 左侧菜单自定义顺序（2026-09-10：功能树拖动排序，全局共享，单条记录） */
    public final JsonStringEntityRepository<MenuOrder> menuOrder;
    public final JsonStringEntityRepository<Doc> doc;
    public final JsonEntityRepository<ChatSession> chatSession;
    public final JsonEntityRepository<ChatMessage> chatMessage;
    public final JsonEntityRepository<Log> log;

    public Repositories(ObjectMapper objectMapper,
                        @Value("${app.data-dir}") String dataDir,
                        PasswordEncoder passwordEncoder) throws IOException {
        Path dir = Path.of(dataDir);

        org = new JsonEntityRepository<>(objectMapper, dir.resolve("system/org.json"),
                new TypeReference<List<Org>>() {}, Org::getId, Org::setId);
        user = new JsonEntityRepository<>(objectMapper, dir.resolve("system/user.json"),
                new TypeReference<List<User>>() {}, User::getId, User::setId);
        account = new JsonEntityRepository<>(objectMapper, dir.resolve("system/account.json"),
                new TypeReference<List<Account>>() {}, Account::getId, Account::setId);
        userOrg = new JsonEntityRepository<>(objectMapper, dir.resolve("system/user-org.json"),
                new TypeReference<List<UserOrg>>() {}, UserOrg::getId, UserOrg::setId);
        userRole = new JsonEntityRepository<>(objectMapper, dir.resolve("system/user-role.json"),
                new TypeReference<List<UserRole>>() {}, UserRole::getId, UserRole::setId);
        role = new JsonEntityRepository<>(objectMapper, dir.resolve("system/role.json"),
                new TypeReference<List<Role>>() {}, Role::getId, Role::setId);
        permission = new JsonEntityRepository<>(objectMapper, dir.resolve("system/permission.json"),
                new TypeReference<List<Permission>>() {}, Permission::getId, Permission::setId);
        rolePermission = new JsonEntityRepository<>(objectMapper, dir.resolve("system/role-permission.json"),
                new TypeReference<List<RolePermission>>() {}, RolePermission::getId, RolePermission::setId);
        dict = new JsonEntityRepository<>(objectMapper, dir.resolve("system/dict.json"),
                new TypeReference<List<Dict>>() {}, Dict::getId, Dict::setId);
        dictField = new JsonEntityRepository<>(objectMapper, dir.resolve("system/dict-field.json"),
                new TypeReference<List<DictField>>() {}, DictField::getId, DictField::setId);
        dictItem = new JsonEntityRepository<>(objectMapper, dir.resolve("system/dict-item.json"),
                new TypeReference<List<DictItem>>() {}, DictItem::getId, DictItem::setId);
        task = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/task.json"),
                new TypeReference<List<Task>>() {}, Task::getId, Task::setId);
        belongProject = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/belong-project.json"),
                new TypeReference<List<BelongProject>>() {}, BelongProject::getId, BelongProject::setId);
        belongTopic = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/belong-topic.json"),
                new TypeReference<List<BelongTopic>>() {}, BelongTopic::getId, BelongTopic::setId);
        belongSubsystem = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/belong-subsystem.json"),
                new TypeReference<List<BelongSubsystem>>() {}, BelongSubsystem::getId, BelongSubsystem::setId);
        module = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/module.json"),
                new TypeReference<List<Module>>() {}, Module::getId, Module::setId);
        topic = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/topic.json"),
                new TypeReference<List<Topic>>() {}, Topic::getId, Topic::setId);
        project = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/project.json"),
                new TypeReference<List<Project>>() {}, Project::getId, Project::setId);
        subsystem = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/subsystem.json"),
                new TypeReference<List<Subsystem>>() {}, Subsystem::getId, Subsystem::setId);
        menuOrder = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/menu-order.json"),
                new TypeReference<List<MenuOrder>>() {}, MenuOrder::getId, MenuOrder::setId);
        doc = new JsonStringEntityRepository<>(objectMapper, dir.resolve("system/doc.json"),
                new TypeReference<List<Doc>>() {}, Doc::getId, Doc::setId);
        chatSession = new JsonEntityRepository<>(objectMapper, dir.resolve("system/chat-session.json"),
                new TypeReference<List<ChatSession>>() {}, ChatSession::getId, ChatSession::setId);
        chatMessage = new JsonEntityRepository<>(objectMapper, dir.resolve("system/chat-message.json"),
                new TypeReference<List<ChatMessage>>() {}, ChatMessage::getId, ChatMessage::setId);
        userSetting = new JsonEntityRepository<>(objectMapper, dir.resolve("system/user-setting.json"),
                new TypeReference<List<UserSetting>>() {}, UserSetting::getId, UserSetting::setId);
        log = new JsonEntityRepository<>(objectMapper, dir.resolve("system/log.json"),
                new TypeReference<List<Log>>() {}, Log::getId, Log::setId);

        seed(objectMapper, passwordEncoder);
    }

    /** 首次启动导入 seed（数据文件不存在时） */
    private void seed(ObjectMapper objectMapper, PasswordEncoder passwordEncoder) throws IOException {
        seedIfAbsent(org, objectMapper, "seed/org.json", new TypeReference<List<Org>>() {});
        seedIfAbsent(user, objectMapper, "seed/user.json", new TypeReference<List<User>>() {});
        seedIfAbsent(account, objectMapper, "seed/account.json", new TypeReference<List<Account>>() {});
        seedIfAbsent(userOrg, objectMapper, "seed/user-org.json", new TypeReference<List<UserOrg>>() {});
        seedIfAbsent(userRole, objectMapper, "seed/user-role.json", new TypeReference<List<UserRole>>() {});
        seedIfAbsent(role, objectMapper, "seed/role.json", new TypeReference<List<Role>>() {});
        seedIfAbsent(permission, objectMapper, "seed/permission.json", new TypeReference<List<Permission>>() {});
        seedIfAbsent(rolePermission, objectMapper, "seed/role-permission.json",
                new TypeReference<List<RolePermission>>() {});
        seedIfAbsent(dict, objectMapper, "seed/dict.json", new TypeReference<List<Dict>>() {});
        seedIfAbsent(dictField, objectMapper, "seed/dict-field.json", new TypeReference<List<DictField>>() {});
        seedIfAbsent(dictItem, objectMapper, "seed/dict-item.json", new TypeReference<List<DictItem>>() {});
        seedIfAbsent(belongProject, objectMapper, "seed/belong-project.json",
                new TypeReference<List<BelongProject>>() {});
        seedIfAbsent(belongTopic, objectMapper, "seed/belong-topic.json",
                new TypeReference<List<BelongTopic>>() {});
        seedIfAbsent(belongSubsystem, objectMapper, "seed/belong-subsystem.json",
                new TypeReference<List<BelongSubsystem>>() {});
        seedIfAbsent(task, objectMapper, "seed/task.json", new TypeReference<List<Task>>() {});
        seedIfAbsent(module, objectMapper, "seed/module.json", new TypeReference<List<Module>>() {});
        seedIfAbsent(topic, objectMapper, "seed/topic.json", new TypeReference<List<Topic>>() {});
        seedIfAbsent(project, objectMapper, "seed/project.json", new TypeReference<List<Project>>() {});
        seedIfAbsent(subsystem, objectMapper, "seed/subsystem.json", new TypeReference<List<Subsystem>>() {});
        seedIfAbsent(doc, objectMapper, "seed/doc.json", new TypeReference<List<Doc>>() {});
        seedIfAbsent(chatSession, objectMapper, "seed/chat-session.json", new TypeReference<List<ChatSession>>() {});
        seedIfAbsent(chatMessage, objectMapper, "seed/chat-message.json", new TypeReference<List<ChatMessage>>() {});

        // seed 账号口令 "SEED:明文" -> BCrypt 哈希
        for (Account acct : account.findAll()) {
            String credential = acct.getCredential();
            if (credential != null && credential.startsWith("SEED:")) {
                acct.setCredential(passwordEncoder.encode(credential.substring("SEED:".length())));
                account.updateById(acct);
            }
        }
    }

    private <T> void seedIfAbsent(JsonEntityRepository<T> repository, ObjectMapper objectMapper,
                                  String resource, TypeReference<List<T>> typeReference) throws IOException {
        if (repository.loadedFromFile()) {
            return;
        }
        ClassPathResource classPathResource = new ClassPathResource(resource);
        if (!classPathResource.exists()) {
            return;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            List<T> seeds = objectMapper.readValue(in, typeReference);
            repository.seedAll(seeds);
        }
    }

    /** 字符串主键仓储的 seed（语义同上方 Long 版本） */
    private <T> void seedIfAbsent(JsonStringEntityRepository<T> repository, ObjectMapper objectMapper,
                                  String resource, TypeReference<List<T>> typeReference) throws IOException {
        if (repository.loadedFromFile()) {
            return;
        }
        ClassPathResource classPathResource = new ClassPathResource(resource);
        if (!classPathResource.exists()) {
            return;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            List<T> seeds = objectMapper.readValue(in, typeReference);
            repository.seedAll(seeds);
        }
    }
}
