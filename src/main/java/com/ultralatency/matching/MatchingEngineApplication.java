package com.ultralatency.matching;

/**
 * Application entry point reserved for the future runtime assembly.
 */
public final class MatchingEngineApplication {

    private static final String APPLICATION_NAME = "Ultra-Low-Latency Matching Engine";

    private MatchingEngineApplication() {
    }

    public static void main(final String[] args) {
        System.out.println(APPLICATION_NAME);
    }

    public static String applicationName() {
        return APPLICATION_NAME;
    }
}
