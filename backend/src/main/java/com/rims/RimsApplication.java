package com.rims;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 科研项目全生命周期管理系统（RIMS）启动类。
 */
@SpringBootApplication
@MapperScan("com.rims.mapper")
public class RimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RimsApplication.class, args);
    }
}
