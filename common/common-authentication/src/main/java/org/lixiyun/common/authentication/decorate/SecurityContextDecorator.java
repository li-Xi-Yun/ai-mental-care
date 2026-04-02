package org.lixiyun.common.authentication.decorate;

/**
 * 异步线程上下文的传递配置
 * @author lixiyun
 * @since 2026-03-30 20:12
 */
//public class SecurityContextDecorator implements TaskDecorator {
//    @Override
//    public Runnable decorate(Runnable runnable) {
//        // 拿到当前线程的认证信息
//        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
//
//        return () -> {
//            try {
//                // 设置到异步线程
//                SecurityContextHolder.getContext().setAuthentication(auth);
//                runnable.run();
//            } finally {
//                SecurityContextHolder.clearContext();
//            }
//        };
//    }
//}