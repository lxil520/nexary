package org.nexary.job.xxljob.boot2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection-based bridge to XXL-JOB runtime APIs without exposing them in public signatures. */
final class XxlJobRuntime {
    private static final String HELPER_CLASS = "com.xxl.job.core.context.XxlJobHelper";

    private XxlJobRuntime() {
    }

    static int currentShardIndex() {
        return invokeInt("getShardIndex", 0);
    }

    static int currentShardTotal() {
        return invokeInt("getShardTotal", 1);
    }

    static void log(String pattern, Object... args) {
        try {
            Class<?> helper = Class.forName(HELPER_CLASS);
            Method method = helper.getMethod("log", String.class, Object[].class);
            method.invoke(null, pattern, args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
        }
    }

    private static int invokeInt(String methodName, int fallback) {
        try {
            Class<?> helper = Class.forName(HELPER_CLASS);
            Method method = helper.getMethod(methodName);
            Object value = method.invoke(null);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return fallback;
        }
    }
}
