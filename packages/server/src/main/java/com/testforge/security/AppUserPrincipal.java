package com.testforge.security;

import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션에 담기는 인증 주체. Spring Security {@link UserDetails}를 구현하여 표준 인증/인가에 태우되,
 * 컨트롤러가 세션에서 userId/role을 직접 도출할 수 있도록 도메인 필드를 함께 노출한다.
 *
 * <p>권한은 {@code ROLE_USER}/{@code ROLE_ADMIN} 형태로 매핑되어 {@code hasRole('ADMIN')} 인가에 쓰인다.
 * password는 인증 후 세션 재확인 흐름에서 다시 쓰지 않으므로 보관하지 않는다(빈 문자열).
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String name;
    private final UserRole role;

    public AppUserPrincipal(Long id, String username, String name, UserRole role) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.role = role;
    }

    /** 엔티티로부터 세션 주체 생성 */
    public static AppUserPrincipal from(AppUser user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getName(), user.getRole());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** 세션 재확인 방식이라 비밀번호는 주체에 보관하지 않는다. */
    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }
}
