package io.github.arnaudroger.tmvl;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class P4 {

    private static final String GUAVA_REPO = "http://repo1.maven.org/maven2/com/google/guava/guava";

    public static void main(String[] args) throws Exception {
        URL targetClassUrl = new File("target/classes").toURI().toURL();
        URLClassLoader urlClassLoader18 = new P4ClassLoader( new URL[] { new URL(GUAVA_REPO + "/18.0/guava-18.0.jar"), targetClassUrl }, P4.class.getClassLoader());
        URLClassLoader urlClassLoader19 = new P4ClassLoader( new URL[] { new URL(GUAVA_REPO + "/19.0/guava-19.0.jar"), targetClassUrl }, P4.class.getClassLoader());

        String userName = System.getProperty("user.name");

        urlClassLoader18.loadClass(GuavaAndCommonUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);
        urlClassLoader19.loadClass(GuavaAndCommonUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);

    }

    public static class P4ClassLoader extends URLClassLoader {

        private final ClassLoader delegate;
        public P4ClassLoader(URL[] urls, ClassLoader delegate) {
            super(urls, null);
            this.delegate = delegate;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            try {
                return super.loadClass(name);
            } catch (Exception e) {
                return delegate.loadClass(name);
            }
        }
    }
}
