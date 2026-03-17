//package org.lixiyun.common.translation.core.impl;
//
//import lombok.AllArgsConstructor;
//import org.lixiyun.common.translation.annotation.TranslationType;
//import org.lixiyun.common.translation.constant.TransConstant;
//import org.lixiyun.common.translation.core.TranslationInterface;
//
///**
// * 用户名翻译实现
// *
// * @author lixiyun
// */
//@AllArgsConstructor
//@TranslationType(type = TransConstant.USER_ID_TO_NAME)
//public class UserNameTranslationImpl implements TranslationInterface<String> {
//
//    private final UserService userService;
//
//    @Override
//    public String translation(Object key, String other) {
//        if (key instanceof Long id) {
//            return userService.selectUserNameById(id);
//        }
//        return null;
//    }
//}
