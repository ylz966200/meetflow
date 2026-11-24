package com.ylz.futuremade.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在录音列表 GET 请求内关闭 MyBatis 调试日志，包含认证和权限查询；异常日志继续保留。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RecordingListSqlLoggingFilter extends OncePerRequestFilter {
    private static final String QUIET_SQL = "recordingListQuietSql";
    private static final Pattern PATH = Pattern.compile("/api/meetings/[^/]+/transcriptions/recordings/?");
    private final TurboFilter sqlFilter = new TurboFilter() {
        @Override
        public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                Object[] arguments, Throwable exception) {
            boolean mybatis = logger.getName().startsWith("org.apache.ibatis.")
                    || logger.getName().startsWith("org.mybatis.")
                    || logger.getName().startsWith("com.ylz.futuremade.") && logger.getName().contains(".mapper.");
            return mybatis && level.toInt() < Level.WARN_INT && "true".equals(MDC.get(QUIET_SQL))
                    ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
    };

    @PostConstruct
    public void install() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        sqlFilter.start();
        context.addTurboFilter(sqlFilter);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"GET".equals(request.getMethod()) || !PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous = MDC.get(QUIET_SQL);
        MDC.put(QUIET_SQL, "true");
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove(QUIET_SQL);
            else MDC.put(QUIET_SQL, previous);
        }
    }

    @PreDestroy
    public void uninstall() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getTurboFilterList().remove(sqlFilter);
        sqlFilter.stop();
    }
}
