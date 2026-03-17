package org.lixiyun.common.authentication.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "url")
public class PermitUrlProperties {

    private List<String> permitAllUrls = new ArrayList<>();

}