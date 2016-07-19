package io.github.arnaudroger.tmvl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class P5LibrarySetClassLoader extends URLClassLoader {
    private final ClassLoader classLoader;

    public P5LibrarySetClassLoader(ClassLoader classLoader, String[] libraries) throws IOException {
        super(toUrls(libraries), Integer.class.getClassLoader());
        this.classLoader = classLoader;
    }

    private static URL[] toUrls(String[] libraries) throws IOException {
        return Arrays.stream(libraries).map((s) -> {
            try {
                if (s.startsWith("file:")) {
                    return new File(s.substring("file:".length())).toURI().toURL();
                }
                return new URL(s);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            return classLoader.loadClass(name);
        }
    }
}
