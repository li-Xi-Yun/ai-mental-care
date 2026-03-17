package org.lixiyun.common.sql.config;

import com.github.pagehelper.PageInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class MyBatisConfig {

    @Bean
        // 注册组件，  使用时无需用@Autowired,用其中的静态方法，放置在方法上方，如：PageHelper.startPage(pageNum(页码),pageSize(单页数目));
    PageInterceptor pageInterceptor() {
        //1、创建 分页插件 对象
        PageInterceptor interceptor = new PageInterceptor();

        //2、设置 参数
        // 创建一个Properties对象，用于存放分页插件的配置参数。
        Properties properties = new Properties();

        // reasonable：分页合理化参数，默认为false，为true时，pageNum<=0时会查询第一页，pageNum>总页数时，会查询最后一页
        // 详细看官网pagehelper.github.io中的文档
        properties.setProperty("reasonable", "true");

        // 将配置参数设置到分页插件中。
        interceptor.setProperties(properties);
        return interceptor;
    }

    // 以上步骤可放在application.properties或application.yml中，配置PageHelper的参数，如：
    // # 分页插件
    // # 用于指定分页插件使用的数据库方言，如果没有设置 pagehelper.helper-dialect 参数，PageHelper 将使用默认的数据库方言MySQL
    // pagehelper.helper-dialect=mysql

    // # 这个参数的作用是允许你在执行分页查询时，自定义用于计算总记录数（count）的 SQL 语句，常用于子查询中
    // pagehelper.params=count=countSql

    // pagehelper.reasonable=true  (一般情况下只配置这个足够了)

    // # 默认情况下，PageHelper 需要你在代码中直接调用 PageHelper.startPage(pageNum, pageSize) 来指定分页参数。
    // 但是，当你启用 support-methods-arguments 功能后，
    // 你可以在 Mapper 接口的方法参数中直接传递分页参数，PageHelper 会自动解析这些参数并应用到分页查询中
    // pagehelper.support-methods-arguments=true
}
