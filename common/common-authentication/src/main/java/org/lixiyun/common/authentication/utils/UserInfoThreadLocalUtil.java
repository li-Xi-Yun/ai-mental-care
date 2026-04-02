package org.lixiyun.common.authentication.utils;

import org.lixiyun.common.core.error.enums.AuthenticationExceptionEnum;
import org.lixiyun.common.core.error.exception.BusinessException;
import org.lixiyun.pojo.tool.BasicsUser;
import org.lixiyun.pojo.tool.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

public class UserInfoThreadLocalUtil {

    public static Optional<LoginUser> getCurrentLoginUser(){
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof String){
            return Optional.empty();
        }
        return Optional.of((LoginUser) principal);
    }

    public static Optional<BasicsUser> getUserInfo(){
        Optional<LoginUser> detailsOpl = getCurrentLoginUser();
        return detailsOpl.map(LoginUser::getBasicsUser);
    }

    public static Optional<Long> getCurrentId() {
        Optional<LoginUser> detailsOpl = getCurrentLoginUser();
        return detailsOpl.map(loginUser -> loginUser.getBasicsUser().getId());
    }

    public static Optional<List<String>> getUserRoles(){
        Optional<LoginUser> currentLoginUserOpl = getCurrentLoginUser();
        return currentLoginUserOpl.map(LoginUser::getRoles);
    }

    public static Optional<List<String>> getUserPermission(){
        Optional<LoginUser> currentLoginUserOpl = getCurrentLoginUser();
        return currentLoginUserOpl.map(LoginUser::getPermissions);
    }


    public static LoginUser getCurrentLoginUserThrow(){
        Optional<LoginUser> currentLoginUser = getCurrentLoginUser();
        return currentLoginUser.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
    }

    public static BasicsUser getUserInfoThrow(){
        Optional<BasicsUser> userInfo = getUserInfo();
        return userInfo.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
    }

    public static Long getCurrentIdThrow() {
        Optional<Long> currentId = getCurrentId();
        return currentId.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
    }

    public static List<String> getUserRolesThrow(){
        Optional<List<String>> userRoles = getUserRoles();
        return userRoles.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
    }

    public static List<String> getUserPermissionThrow(){
        Optional<List<String>> userPermission = getUserPermission();
        return userPermission.orElseThrow(() -> new BusinessException(AuthenticationExceptionEnum.USER_NOT_LOGIN));
    }

    public static void setSecurityContext(Principal principal){
        SecurityContextHolder.getContext().setAuthentication((Authentication) principal);
    }


}
