//package org.lixiyun.common.translation.core.impl;
//
//import lombok.AllArgsConstructor;
//import org.lixiyun.common.translation.annotation.TranslationType;
//import org.lixiyun.common.translation.constant.TransConstant;
//import org.lixiyun.common.translation.core.TranslationInterface;
//
///**
// * OSS翻译实现
// *
// * @author lixiyun
// */
//@AllArgsConstructor
//@TranslationType(type = TransConstant.OSS_ID_TO_URL)
//public class OssUrlTranslationImpl implements TranslationInterface<String> {
//
//    private final OssService ossService;
//
//    @Override
//    public String translation(Object key, String other) {
//        if (key instanceof String ids) {
//            return ossService.selectUrlByIds(ids);
//        } else if (key instanceof Long id) {
//            return ossService.selectUrlByIds(id.toString());
//        }
//        return null;
//    }
//}
