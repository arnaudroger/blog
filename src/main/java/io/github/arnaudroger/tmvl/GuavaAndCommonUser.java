package io.github.arnaudroger.tmvl;

import com.google.common.base.Ascii;
import org.apache.commons.lang3.StringUtils;

import java.net.URLClassLoader;
import java.util.Arrays;

public class GuavaAndCommonUser {
    public static String toLowerCase(String str) {
        printClassLoader(GuavaAndCommonUser.class);
        printClassLoader(Ascii.class);
        printClassLoader(StringUtils.class);

        if (!StringUtils.isAllUpperCase(str)) {
            return Ascii.toUpperCase(str);
        }
        return str;
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
