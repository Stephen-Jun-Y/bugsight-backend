package com.bugsight.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigCorsTest {

    @Test
    void allowsCorsPreflightForRecognitionUpload() throws Exception {
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
        new WebConfig().addInterceptors(registry);

        HandlerInterceptor interceptor = extractInterceptor(registry.getRegistered().get(0));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/recognitions");
        request.addHeader("Origin", "http://127.0.0.1:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        assertTrue(allowed);
    }

    private HandlerInterceptor extractInterceptor(Object registered) {
        if (registered instanceof MappedInterceptor mappedInterceptor) {
            return mappedInterceptor;
        }
        return (HandlerInterceptor) registered;
    }

    private static class ExposedInterceptorRegistry extends InterceptorRegistry {
        public List<Object> getRegistered() {
            return super.getInterceptors();
        }
    }
}
