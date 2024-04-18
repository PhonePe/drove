package com.phonepe.drove.controller.masking;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.common.Mask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.ClassUtils;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * This will mask all fields annotated with {@link Mask}
 */
@EnforceMasking
@Provider
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class MaskingResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext) throws IOException {
        val type = responseContext.getEntityClass();
        if (type.equals(ApiResponse.class)) {
            val response = (ApiResponse<?>) responseContext.getEntity();
            mask(response.getData(), Object.class);
            responseContext.setEntity(response);
        }
    }

    private <T> void mask(T object, Class<?> callingType) {
        if (null == object || callingType.equals(object.getClass())) {
            return;
        }
        for (Field field : object.getClass().getDeclaredFields()) {
            log.info("Handling field: {}::{} of type: {}",
                     object.getClass().getSimpleName(),
                     field.getName(),
                     field.getType().getSimpleName());
            if (Objects.nonNull(field.getDeclaredAnnotation(Mask.class))) {
                log.info("Found masked field: {}", field.getName());
                try {
                    if (field.getType() == String.class) {
                        field.setAccessible(true);
                        field.set(object, "***");
                    }
                    else {
                        if (field.getType().isArray()) {
                            field.setAccessible(true);
                            field.set(object, Array.newInstance(field.getType().getComponentType(), 0));
                        }
                    }
                }
                catch (IllegalAccessException e) {
                    log.error("Could not maks field: {}", field.getName());
                }
            }
            else {
                if (ClassUtils.isPrimitiveOrWrapper(field.getType())
                        || field.getType().isEnum()) {
                    continue;
                }
                if (ClassUtils.isAssignable(field.getType(), Collection.class)) {
                    field.setAccessible(true);
                    try {
                        val values = (Collection<?>) field.get(object);
                        if (null != values) {
                            values.forEach(value -> mask(value, Object.class));
                        }
                    }
                    catch (IllegalAccessException e) {
                        log.error("Could not get collection: {}::{}",
                                  object.getClass().getSimpleName(),
                                  field.getName());
                    }
                }
                else if (ClassUtils.isAssignable(field.getType(), Map.class)) {
                    field.setAccessible(true);
                    try {
                        val map = (Map<?, ?>) field.get(object);
                        if (null != map) {
                            map.values().forEach(value -> mask(value, Object.class));
                        }
                    }
                    catch (IllegalAccessException e) {
                        log.error("Could not get map: {}::{}", object.getClass().getSimpleName(), field.getName());
                    }
                }
                else {
                    //Handle class objects
                    if (field.getType().getPackageName().startsWith("com.phonepe.drove")) {
                        try {
                            field.setAccessible(true);
                            val value = field.get(object);
                            if (null != value) {
                                mask(value, object.getClass());
                            }
                        }
                        catch (IllegalAccessException e) {
                            log.error("Error masking field: {}. Cause: {}", field.getName(), e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
