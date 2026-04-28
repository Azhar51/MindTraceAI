package android.util;

/**
 * Stub implementation of {@code android.util.Base64} for unit tests.
 *
 * <p>The real Android {@code Base64} class is unavailable in JVM unit tests.
 * This stub delegates to {@link java.util.Base64} and is automatically loaded
 * by the JVM classloader because the test classpath takes priority over
 * the android.jar stubs.</p>
 */
public class Base64 {
    public static final int NO_WRAP = 2;

    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        return java.util.Base64.getDecoder().decode(str);
    }

    public static byte[] encode(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encode(input);
    }
}
