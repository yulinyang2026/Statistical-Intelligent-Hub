package com.bsp.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BSP 管理后台后端启动类
 *
 * <p>一期数据落地为 JSON 文件存储（见 storage 包），二期切换达梦 DM8。</p>
 */
@SpringBootApplication
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
