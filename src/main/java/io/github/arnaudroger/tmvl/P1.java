package io.github.arnaudroger.tmvl;

import java.net.URL;
import java.net.URLClassLoader;

public class P1 {

    private static final String GUAVA_REPO = "http://repo1.maven.org/maven2/com/google/guava/guava";

    public static void main(String[] args) throws Exception {
        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
        URLClassLoader urlClassLoader18 = new URLClassLoader(
                new URL[] { new URL(GUAVA_REPO + "/18.0/guava-18.0.jar") },
                currentClassLoader);
        URLClassLoader urlClassLoader19 = new URLClassLoader(
                new URL[] { new URL(GUAVA_REPO + "/19.0/guava-19.0.jar") },
                currentClassLoader);

        Class<?> asciiClass18 = urlClassLoader18.loadClass("com.google.common.base.Ascii");
        Class<?> asciiClass19 = urlClassLoader19.loadClass("com.google.common.base.Ascii");

        System.out.println("asciiClass18.equals(asciiClass19) = " + asciiClass18.equals(asciiClass19));

        System.out.println("18 - Ascii.toUpperCase('c') = " + asciiClass18.getMethod("toUpperCase", char.class).invoke(null, 'c'));
        System.out.println("19 - Ascii.toUpperCase('c') = " + asciiClass19.getMethod("toUpperCase", char.class).invoke(null, 'c'));

        GuavaUser.toLowerCase(System.getProperty("user.name"));

    }
}
