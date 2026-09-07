package org.lixiyun.server.config;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置类
 * <p>
 * 配置API文档分组，分为管理员接口和用户接口
 * </p>
 *
 * @author lixiyun
 * @since 2026-05-06
 */
@Configuration
public class SpringDocConfig {

    /**
     * 管理员接口分组
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .pathsToMatch("/admin/**")
                .packagesToScan("org.lixiyun.server.controller.admin")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("心聆AI系统 - 管理员接口")
                            .description("后台管理员相关接口文档")
                            .version("1.0.0")
                            .contact(new Contact()
                                    .name("离晞云")
                                    .email("lixiyun@example.com")));
                    // 配置开源许可证
//                            .license(new License()
//                                    .name("Apache 2.0")
//                                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
                })
                .build();
    }

    /**
     * 用户接口分组
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user-api")
                .pathsToMatch("/user/**", "/ws/**", "/socket/**")
                .packagesToScan("org.lixiyun.server.controller.user")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("心聆AI系统 - 用户接口")
                            .description("前台用户相关接口文档")
                            .version("1.0.0")
                            .contact(new Contact()
                                    .name("离晞云")
                                    .email("lixiyun@example.com")));
                    // 配置开源许可证
//                            .license(new License()
//                                    .name("Apache 2.0")
//                                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
                })
                .build();
    }

    /**
     * 公共接口分组
     */
    @Bean
    public GroupedOpenApi commonApi() {
        return GroupedOpenApi.builder()
                .group("common-api")
                .pathsToMatch("/common/**")
                .packagesToScan("org.lixiyun.server.controller.common")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("心聆AI系统 - 公共接口")
                            .description("公共相关接口文档")
                            .version("1.0.0")
                            .contact(new Contact()
                                    .name("离晞云")
                                    .email("lixiyun@example.com")));
                    // 配置开源许可证
//                            .license(new License()
//                                    .name("Apache 2.0")
//                                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
                })
                .build();
    }

    /**
     * 临时接口分组
     */
    @Bean
    public GroupedOpenApi tempApi() {
        return GroupedOpenApi.builder()
                .group("temp-api")
                .pathsToMatch("/temp/**")
                .packagesToScan("org.lixiyun.server.controller.temp")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("心聆AI系统 - 临时接口")
                            .description("临时相关接口文档")
                            .version("1.0.0")
                            .contact(new Contact()
                                    .name("离晞云")
                                    .email("lixiyun@example.com")));
                    // 配置开源许可证
//                            .license(new License()
//                                    .name("Apache 2.0")
//                                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
                })
                .build();
    }

    /**
     * Agent 接口分组
     */
    @Bean
    public GroupedOpenApi agentApi() {
        return GroupedOpenApi.builder()
                .group("agent-api")
                .pathsToMatch("/prompt/**", "/skill/**")
                .packagesToScan("org.lixiyun.common.agent.skill.controller", "org.lixiyun.common.agent.prompt.controller", "org.lixiyun.server.controller.temp")
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("心聆AI系统 - Agent接口")
                            .description("Agent相关接口文档")
                            .version("1.0.0")
                            .contact(new Contact()
                                    .name("离晞云")
                                    .email("lixiyun@example.com")));
                    // 配置开源许可证
//                            .license(new License()
//                                    .name("Apache 2.0")
//                                    .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
                })
                .build();
    }
}
