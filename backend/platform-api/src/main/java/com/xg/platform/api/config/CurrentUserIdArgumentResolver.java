package com.xg.platform.api.config;

import com.xg.platform.contracts.validation.PlatformIds;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_HEADER = "X-User-Id";

    private final String fallbackUserId;

    public CurrentUserIdArgumentResolver(String fallbackUserId) {
        this.fallbackUserId = fallbackUserId;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String requestedUserId = webRequest.getHeader(USER_HEADER);
        if (requestedUserId == null || requestedUserId.isBlank()) {
            requestedUserId = fallbackUserId;
        }
        return PlatformIds.requireUserId(requestedUserId);
    }
}
