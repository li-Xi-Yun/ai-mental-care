//package org.lixiyun.common.translation.core.impl;
//
//import cn.hutool.core.util.StrUtil;
//import lombok.AllArgsConstructor;
//import org.lixiyun.common.translation.annotation.TranslationType;
//import org.lixiyun.common.translation.constant.TransConstant;
//import org.lixiyun.common.translation.core.TranslationInterface;
//
///**
// * 字典翻译实现
// *
// * @author lixiyun
// */
//@AllArgsConstructor
//@TranslationType(type = TransConstant.DICT_TYPE_TO_LABEL)
//public class DictTypeTranslationImpl implements TranslationInterface<String> {
//
//    private final DictService dictService;
//
//    @Override
//    public String translation(Object key, String other) {
//        if (key instanceof String dictValue && StrUtil.isNotBlank(other)) {
//            return dictService.getDictLabel(other, dictValue);
//        }
//        return null;
//    }
//}
