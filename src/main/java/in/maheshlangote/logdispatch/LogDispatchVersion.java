package in.maheshlangote.logdispatch;

/**
 * Provides dynamic SDK version and language metadata.
 */
public final class LogDispatchVersion {

    public static final String SDK_LANGUAGE = "java";
    private static final String DEFAULT_VERSION = "0.0.0";

    private LogDispatchVersion() {
        // Utility class
    }

    /**
     * Dynamically retrieves the SDK version from the JAR Manifest,
     * defaulting to "0.0.0" if unavailable.
     *
     * @return the SDK version string
     */
    public static String getSdkVersion() {
        try {
            String version = LogDispatchVersion.class.getPackage().getImplementationVersion();
            if (version != null && !version.isBlank()) {
                return version.trim();
            }
        } catch (Exception ignored) {
            // Fallback
        }
        return DEFAULT_VERSION;
    }
}
