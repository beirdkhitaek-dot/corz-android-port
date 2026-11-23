package dev.dov.hack.echo.client.modules.impl.internals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public record StringMemoryAccess(Object unsafe, Method objectFieldOffset, Method staticFieldOffset, Method staticFieldBase, Method getObject, Method putObject) {
    
    public static StringMemoryAccess create() {
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field singleton = type.getDeclaredField("theUnsafe");
            if (!singleton.trySetAccessible()) {
                return null;
            }
            Object instance = singleton.get(null);
            return new StringMemoryAccess(instance, type.getMethod("objectFieldOffset", Field.class), type.getMethod("staticFieldOffset", Field.class), type.getMethod("staticFieldBase", Field.class), type.getMethod("getObject", Object.class, long.class), type.getMethod("putObject", Object.class, long.class, Object.class));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    
    public void clearReference(Field field, Object owner) {
        try {
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            Object base = isStatic ? staticFieldBase.invoke(unsafe, field) : owner;
            Method offsetQuery = isStatic ? staticFieldOffset : objectFieldOffset;
            long offset = (Long) offsetQuery.invoke(unsafe, field);
            putObject.invoke(unsafe, base, offset, null);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
        }
    }

    
    public void clearString(String string) {
        if (string == null || string.isEmpty()) {
            return;
        }
        try {
            Field value = String.class.getDeclaredField("value");
            long offset = (Long) objectFieldOffset.invoke(unsafe, value);
            Object storage = getObject.invoke(unsafe, string, offset);
            if (storage instanceof byte[] bytes) {
                Arrays.fill(bytes, (byte) 0);
            } else if (storage instanceof char[] characters) {
                Arrays.fill(characters, (char) 0);
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
        }
    }
}
