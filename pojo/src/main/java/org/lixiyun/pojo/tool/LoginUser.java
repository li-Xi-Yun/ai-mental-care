package org.lixiyun.pojo.tool;

import cn.hutool.core.annotation.PropIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 用来封装登录用户信息和权限信息的UserDetails对象
 * UserDetails接口只定义了获取数据的访问方法，没有定义设置数据的方法，那么我们需要自定义
 */
@Data
@NoArgsConstructor
public class LoginUser implements UserDetails, OAuth2User {

    // 用户信息对象
    private BasicsUser basicsUser;

    // 用户角色列表
    private List<String> roles;

    // 用户权限列表
    private List<String> permissions;

    // 存储第三方平台的权限信息
    private Map<String, Object> attributes;

    // 存储第三方平台返回的access_token
    private String access_token;

    // 告知用户是否进行账号合并，true表示进行账号合并，false表示不进行账号合并
//    @JsonIgnore
//    @JSONField(serialize = false, deserialize = false) // 不序列化该字段，这个一定需要
    @PropIgnore
    private boolean merge;
    
    //存储SpringSecurity所需要的权限信息的集合
//    @JSONField(serialize = false, deserialize = false) // 不序列化该字段，这个一定需要
//    @JsonIgnore
    @PropIgnore
    private List<GrantedAuthority> authorities;

    public LoginUser(BasicsUser basicsUser, List<String> permissions, List<String> roles) {
        this(basicsUser, permissions, roles, null, false);
    }
    
    public LoginUser(BasicsUser basicsUser, List<String> permissions, List<String> roles, Map<String, Object> attributes) {
        this(basicsUser, permissions, roles, attributes, false);
    }

    public LoginUser(BasicsUser basicsUser, List<String> permissions, List<String> roles, Map<String, Object> attributes, boolean merge) {
        this.basicsUser = basicsUser;
        this.roles = roles;
        this.permissions = permissions;
        this.attributes = attributes;
        this.merge = merge;
    }


    /**
     * 获取第三方平台权限信息
     */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 获取用户权限列表信息
     */
    // 用户的权限集
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 优化：第一次转换，后续调用直接返回
        if (authorities != null) {
            return authorities;
        }

        //把permissions中字符串类型的权限信息转换成GrantedAuthority对象存入authorities中
//        roles = roles.stream().map(role ->  "ROLE_" + role).toList();

        roles = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .toList();
        authorities = Stream.of(roles, permissions)
                .flatMap(Collection::stream)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return authorities;
    }

    /**
     * 获取用户密码
     */
    @Override
    public String getPassword() {
        return basicsUser.getPassword();
    }

    /**
     * 获取用户名
     */
    @Override
    public String getUsername() {
        return basicsUser.getId().toString();
    }

    /**
     * 判断账户未过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 判断账户未锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return basicsUser.getStatus() == 0;
    }

    /**
     * 判断凭证未过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 判断是否可用
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     *提供用户的唯一标识符
     */
    @Override
    public String getName() {
        return basicsUser.getId().toString();
    }
}