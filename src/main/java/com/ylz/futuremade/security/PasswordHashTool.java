package com.ylz.futuremade.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 手动生成 BCrypt 哈希；只供支持隐藏输入的本地终端使用。 */
public final class PasswordHashTool {
    private PasswordHashTool() { }

    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("请使用支持隐藏输入的独立终端运行");
        char[] password = console.readPassword("请输入密码（至少 6 个字符）：");
        if (password == null) throw new IllegalArgumentException("未输入密码");
        try {
            if (password.length < 6 || new String(password).getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("密码至少 12 个字符，且 UTF-8 编码不超过 72 字节");
            }
            // 哈希作为用户主动请求的结果显示在终端，不进入应用日志。
            console.printf("BCrypt 哈希：%s%n", new BCryptPasswordEncoder().encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
