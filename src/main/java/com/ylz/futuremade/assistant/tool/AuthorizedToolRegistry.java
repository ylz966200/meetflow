package com.ylz.futuremade.assistant.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import com.ylz.futuremade.assistant.mapper.ToolPermissionMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;


import java.util.*;

@Component
public class AuthorizedToolRegistry {
    private final ToolPermissionMapper permissionMapper;
    private final ApplicationContext context;

    public AuthorizedToolRegistry(ToolPermissionMapper permissionMapper, ApplicationContext context) {
        this.permissionMapper = permissionMapper;
        this.context = context;
    }

    public ToolCallback[] forUser(String username) {
        if (username == null || username.isBlank()) {
            return new ToolCallback[0];
        }
        try {
            List<ToolCallback> result = new ArrayList<>();
            HashSet<String> allowed = permissionMapper.findEnabledToolNames(username.trim());
            if (allowed.isEmpty()) return new ToolCallback[0];
            Map<String, ToolCallback> registered = new HashMap<>();
            for (String beanName : context.getBeanDefinitionNames()) {
                Class<?> type = context.getType(beanName);
                if (type == null || !ClassUtils.getUserClass(type).getPackageName().startsWith("com.ylz.futuremade")) continue;
                if (!hasToolMethod(type)) continue;
                for (ToolCallback callback : ToolCallbacks.from(context.getBean(beanName))) {
                    String name = callback.getToolDefinition().name();
                    if(allowed.contains(name)){
                        result.add(callback);
                    }
                    if (registered.putIfAbsent(name, callback) != null) throw new IllegalStateException("Duplicate tool name: " + name);
                }
            }
            return result.toArray(ToolCallback[]::new);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to load tool permissions", ex);
        }
    }

    private boolean hasToolMethod(Class<?> type) {
        return ReflectionUtils.getUniqueDeclaredMethods(type, method -> method.isAnnotationPresent(Tool.class)).length > 0;
    }
}



