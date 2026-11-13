package com.ylz.futuremade.identity.service;

import com.ylz.futuremade.identity.mapper.AccountMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AccountMapper mapper;

    public DatabaseUserDetailsService(AccountMapper mapper) { this.mapper = mapper; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AccountMapper.Account account = mapper.findByUsername(username);
        if (account == null) throw new UsernameNotFoundException("用户名或密码错误");
        String[] roles = mapper.findRoleCodes(account.id()).toArray(String[]::new);
        return User.withUsername(account.username()).password(account.passwordHash())
                .disabled(!account.enabled()).roles(roles).build();
    }
}
