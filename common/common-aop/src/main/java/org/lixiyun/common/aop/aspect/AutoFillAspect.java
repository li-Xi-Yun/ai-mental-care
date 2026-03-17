package org.lixiyun.common.aop.aspect;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.lixiyun.common.aop.annotation.SQLType;
import org.lixiyun.common.aop.constant.AutoFillConstant;
import org.lixiyun.common.authentication.utils.UserInfoThreadLocalUtil;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("execution(* org.lixiyun.*.*(..))  && @annotation(org.lixiyun.common.aop.annotation.SQLType)")
    public void point(){}


    @Before("point() && @annotation(annotation)")
    public void autoFill(JoinPoint joinPoint, SQLType annotation) {
        log.info("对象属性自动填充切面方法开始");

        // 获得方法的第一个参数
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0){
            return;
        }
        Object arg = args[0];

        // 获得当前的时间和操作人id
        LocalDateTime localDateTime = LocalDateTime.now();

        if (arg instanceof List<?>) {
            for (Object o : (List<?>) arg) {
                fillCreateAndUpdate(annotation, localDateTime, o);
            }
        } else {
            fillCreateAndUpdate(annotation, localDateTime, arg);
        }

    }

    private void fillCreateAndUpdate(SQLType annotation, LocalDateTime localDateTime, Object o) {
        // 填充雪花id
        long customSnowflakeId = IdUtil.getSnowflake(23, 17).nextId();

        // 获得当前的时间和操作人id
        LoginUser user = null;
        Long uuid = null;
        try {
            user = UserInfoThreadLocalUtil.getCurrentLoginUserThrow();
            uuid = user.getBasicsUser().getId();
        } catch (Exception e) {
            uuid = customSnowflakeId;
        }


        // 进行注解判断
        if(annotation.value() == SQLType.OperationType.INSERT){
            // 对象属性封装
            try {
                // 填充唯一标识uuid
                Method method = o.getClass().getDeclaredMethod(AutoFillConstant.SET_ID, Long.class);
                method.invoke(o, customSnowflakeId);
            } catch (Exception e) {
                log.info("填充唯一标识uuid属性失败: {}", e.getMessage());
            }

//            try {
//                // 填充创建时间
//                Method method1 = o.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATED_TIME, LocalDateTime.class);
//                method1.invoke(o, localDateTime);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            try {
                // 填充创建人id
                Method method2 = o.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATED_BY, Long.class);
                method2.invoke(o, uuid);
            } catch (Exception e) {
                log.info("填充创建人id属性失败: {}", e.getMessage());
            }
        }

//        try {
//            // 填充更新时间
//            Method method3 = o.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATED_TIME, LocalDateTime.class);
//            method3.invoke(o, localDateTime);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        try {
            // 填充更新人id
            Method method4 = o.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATED_BY, Long.class);
            method4.invoke(o, uuid);
        } catch (Exception e) {
            log.info("填充更新人id属性失败: {}", e.getMessage());
        }
    }


}
