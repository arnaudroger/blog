package io.github.arnaudroger.tmvl;

import com.google.common.base.Ascii;

import java.net.URLClassLoader;
import java.util.Arrays;

public class GuavaUser {
    public static String toLowerCase(String str) {
        printClassLoader(GuavaUser.class);
        printClassLoader(Ascii.class);
        return Ascii.toLowerCase(str);
    }

    private static void printClassLoader(Class<?> target) {
        ClassLoader classLoader = target.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            System.out.println(target.getName() + "/classLoader = " + Arrays.asList(((URLClassLoader) classLoader).getURLs()));
        } else {
            System.out.println(target.getName() + "/classLoader = " + classLoader);
        }
    }
}
