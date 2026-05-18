package com.example.purchaseconversion.api.advice;

import com.example.purchaseconversion.api.dto.PurchaseRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;

/**
 * Drives {@link ContentGuard} immediately after Jackson has bound the request
 * body but BEFORE the controller method is invoked. This is the earliest hook
 * at which the typed {@code description} field is available.
 *
 * <p>Per G8-P0-1, this advice runs AFTER the rate-limit filter (servlet-layer)
 * and BEFORE the controller method. Bean Validation runs in between; a
 * PAN-pattern hit raises {@link com.example.purchaseconversion.api.advice.exception.PanPatternDetectedException}
 * which {@link ProblemDetailExceptionHandler} maps to {@code 400 PAN_PATTERN_DETECTED}.
 */
@ControllerAdvice
public class ContentGuardAdvice extends RequestBodyAdviceAdapter {

    private final ContentGuard contentGuard;

    public ContentGuardAdvice(ContentGuard contentGuard) {
        this.contentGuard = contentGuard;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return PurchaseRequest.class.equals(targetType);
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof PurchaseRequest req) {
            contentGuard.check(req.description());
        }
        return body;
    }
}
