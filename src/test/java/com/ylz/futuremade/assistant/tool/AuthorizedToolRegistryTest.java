package com.ylz.futuremade.assistant.tool;


import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationContext;
import com.ylz.futuremade.assistant.mapper.ToolPermissionMapper;
import com.ylz.futuremade.assistant.mapper.WeatherConfigMapper;

import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizedToolRegistryTest {
    @Test
    void exposesOnlyToolsGrantedToAuthenticatedUser() {
        ToolPermissionMapper permissionMapper = mock(ToolPermissionMapper.class);
        WeatherConfigMapper configMapper = mock(WeatherConfigMapper.class);
        ApplicationContext context = mock(ApplicationContext.class);
        WeatherTools weather = new WeatherTools(configMapper);
        when(context.getBeanDefinitionNames()).thenReturn(new String[]{"weatherTools"});
        when(context.getType("weatherTools")).thenReturn((Class) WeatherTools.class);
        when(context.getBean("weatherTools")).thenReturn(weather);
        when(permissionMapper.findEnabledToolNames("alice")).thenReturn(new HashSet<>(Set.of("currentWeather")));
        when(permissionMapper.findEnabledToolNames("bob")).thenReturn(new HashSet<>());
        AuthorizedToolRegistry registry = new AuthorizedToolRegistry(permissionMapper, context);
        ToolCallback[] granted = registry.forUser("alice");
        assertEquals(1, granted.length);
        assertEquals("currentWeather", granted[0].getToolDefinition().name());
        assertEquals(0, registry.forUser("bob").length);
        assertEquals(0, registry.forUser(null).length);
    }
}


