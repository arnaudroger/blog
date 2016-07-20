package io.github.arnaudroger.tmvl;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class P2 {

    private static final String GUAVA_REPO = "http://repo1.maven.org/maven2/com/google/guava/guava";

    public static void main(String[] args) throws Exception {
        URL targetClassUrl = new File("target/classes").toURI().toURL();
        URLClassLoader urlClassLoader18 = new URLClassLoader( new URL[] { new URL(GUAVA_REPO + "/18.0/guava-18.0.jar"), targetClassUrl }, null);
        URLClassLoader urlClassLoader19 = new URLClassLoader( new URL[] { new URL(GUAVA_REPO + "/19.0/guava-19.0.jar"), targetClassUrl }, null);

        String userName = System.getProperty("user.name");

        urlClassLoader18.loadClass(GuavaUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);
        urlClassLoader19.loadClass(GuavaUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);

    }
}
