package io.github.arnaudroger.tmvl;

import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class P5MultiClassLoaderJunitRunner extends Suite {

    private static final List<Runner> NO_RUNNERS = Collections.emptyList();

    public P5MultiClassLoaderJunitRunner(Class<?> klass) throws Throwable {
        super(klass, buildRunners(klass));
    }

    private static List<Runner> buildRunners(Class<?> klass) throws IOException, ClassNotFoundException, InitializationError {
        P5LibrarySets librarySet = klass.getAnnotation(P5LibrarySets.class);
        if (librarySet == null) throw new IllegalArgumentException("Class " + klass + " is missing P5LibrarySets annotation");

        List<Runner> runners = new ArrayList<Runner>();
        int i = 0;
        for(final String urlsList : librarySet.librarySets()) {
            final String suffix = String.valueOf(i);

            ClassLoader classLoader = new P5LibrarySetClassLoader(Thread.currentThread().getContextClassLoader(), urlsList.split(","));

            Class<?> testClass = classLoader.loadClass(klass.getName());

            BlockJUnit4ClassRunner junit4Runner = new BlockJUnit4ClassRunner(testClass) {
                @Override
                protected String getName() {
                    return super.getName() + suffix;
                }
            };

            runners.add(new P5ClassLoaderChangerRunner(classLoader, junit4Runner));
            i++;
        }
        return runners;
    }
}
