# How to write unit tests that run against multiple version of a library

## Why?

When you are writing a library it might run against different version of other component.
And you might want to support all those versions. I was faced with the that problem with the datastax
module of sfm. It was written agains version 2.1.8 but version 3.0.3 has been recently released
and it would be a shame to not make it also make it work against that version.

On the code side I can use reflection, or method reference to deal with the changes. For the test side though
I started by created a new module to test against datastax 3.0.3. The problem is that it introduce a lot duplication.
And new test in the orginal module needs to be backported to the 3.0.3 one.

Would it not be nice to be able to run the regular test against both version of the library?

## ClassLoader to the rescue

A servlet container needs to be able to load classes in isolation for each webapp, to do that each webapp as its own
classloader. If a class loaded from 2 different ClassLoader are not Equals.
We can use the same mechanism to load both version of the library each with each own ClassLoader.

[P1.java](src/main/java/io/github/arnaudroger/tmvl/P1.java)
```java
URLClassLoader urlClassLoader18 = new URLClassLoader(
        new URL[] { new URL(GUAVA_REPO + "/18.0/guava-18.0.jar") },
        currentClassLoader);
URLClassLoader urlClassLoader19 = new URLClassLoader(
        new URL[] { new URL(GUAVA_REPO + "/19.0/guava-19.0.jar") },
        currentClassLoader);

Class<?> asciiClass18 = urlClassLoader18.loadClass("com.google.common.base.Ascii");
Class<?> asciiClass19 = urlClassLoader19.loadClass("com.google.common.base.Ascii");

System.out.println("asciiClass18.equals(asciiClass19) = " + asciiClass18.equals(asciiClass19));

System.out.println("18 - Ascii.toUpperCase('c') = "
    + asciiClass18.getMethod("toUpperCase", char.class).invoke(null, 'c'));
System.out.println("19 - Ascii.toUpperCase('c') = "
    + asciiClass19.getMethod("toUpperCase", char.class).invoke(null, 'c'));
```

That code load version 18 and 19 of the Ascii class and execute the toUpperCase on each.

```
asciiClass18.equals(asciiClass19) = false
18 - Ascii.toUpperCase('c') C
19 - Ascii.toUpperCase('c') C
```

The problem with that is that we need to use reflection to access the class methods.
for that class

[GuavaUser.java](src/main/java/io/github/arnaudroger/tmvl/GuavaUser.java)
```java
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
```
if we call

```java
    GuavaUser.toLowerCase(System.getProperty("user.name"));

```

we can see that the Ascii class is loaded by the currentClassLoader not our isolated classLoader.
For GuavaUser to be linked to the specific version we also need to load it from the same ClassLoader.
if we just do

```
urlClassLoader18.loadClass(GuavaUser.class.getName());
```

urlClassLoader18 will load GuavaUser from it's parent class loader that is currentClassLoader.
We need to load GuavaUser from the UrlClassLoader by adding the location to list of URL. But because
the classloader load from the parent ClassLoader first we also need to pass null as the parent.

[P2.java](src/main/java/io/github/arnaudroger/tmvl/P2.java)
```java
    URL targetClassUrl = new File("target/classes").toURI().toURL();
    URLClassLoader urlClassLoader18 = new URLClassLoader(
        new URL[] { new URL(GUAVA_REPO + "/18.0/guava-18.0.jar"), targetClassUrl },
        null); // null parent
    URLClassLoader urlClassLoader19 = new URLClassLoader(
        new URL[] { new URL(GUAVA_REPO + "/19.0/guava-19.0.jar"), targetClassUrl },
        null); // null parent

    String userName = System.getProperty("user.name");

    urlClassLoader18.loadClass(GuavaUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);
    urlClassLoader19.loadClass(GuavaUser.class.getName()).getMethod("toLowerCase", String.class).invoke(null, userName);
```

the we get a different class loader for each GuavaUser class

```
io.github.arnaudroger.tmvl.GuavaUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
io.github.arnaudroger.tmvl.GuavaUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
```

Now what if we use a Class that also need another dep?

[P3.java](src/main/java/io/github/arnaudroger/tmvl/P3.java)

[GuavaAndCommonUser.java](src/main/java/io/github/arnaudroger/tmvl/GuavaAndCommonUser.java)
```java
public static String toLowerCase(String str) {
    printClassLoader(GuavaAndCommonUser.class);
    printClassLoader(Ascii.class);
    printClassLoader(StringUtils.class);

    if (!StringUtils.isAllUpperCase(str)) {
        return Ascii.toUpperCase(str);
    }
    return str;
}
```

the we get

```
Caused by: java.lang.ClassNotFoundException: org.apache.commons.lang3.StringUtils
	at java.net.URLClassLoader.findClass(URLClassLoader.java:381)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:424)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
	... 11 more
```

...
The class loader could not resolve commons-lang3. We just need to create our own ClassLoader that will delegate to
the app classloader when it does not find a class.

[P4.java](src/main/java/io/github/arnaudroger/tmvl/P4.java)
```java
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
```

the loop is looped. for now.

```
io.github.arnaudroger.tmvl.GuavaAndCommonUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
org.apache.commons.lang3.StringUtils/classLoader = [file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/ant-javafx.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/dt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/javafx-mx.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/jconsole.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/packager.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/sa-jdi.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/tools.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/charsets.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/deploy.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/javaws.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jce.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jfr.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jfxswt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jsse.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/management-agent.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/plugin.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/resources.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/rt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/cldrdata.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/dnsns.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/jfxrt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/localedata.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/nashorn.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunec.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/zipfs.jar, file:/Users/aroger/dev/github/blog/target/classes/, file:/Users/aroger/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/.m2/repository/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar, file:/Applications/IntelliJ%20IDEA%202016.2%20CE%20EAP.app/Contents/lib/idea_rt.jar]
io.github.arnaudroger.tmvl.GuavaAndCommonUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
org.apache.commons.lang3.StringUtils/classLoader = [file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/ant-javafx.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/dt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/javafx-mx.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/jconsole.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/packager.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/sa-jdi.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/lib/tools.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/charsets.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/deploy.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/javaws.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jce.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jfr.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jfxswt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/jsse.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/management-agent.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/plugin.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/resources.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/rt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/cldrdata.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/dnsns.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/jfxrt.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/localedata.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/nashorn.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunec.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar, file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.jdk/Contents/Home/jre/lib/ext/zipfs.jar, file:/Users/aroger/dev/github/blog/target/classes/, file:/Users/aroger/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/.m2/repository/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar, file:/Applications/IntelliJ%20IDEA%202016.2%20CE%20EAP.app/Contents/lib/idea_rt.jar]
```


## Junit Runner

Now that we have the code for our ClassLoader that works it would be nice to have that integrate in Junit.
So you can just annotate your Class with the library dependencies you need and it create suite of test for each library set.

That would work like that

[P5Test.java](src/test/java/io/github/arnaudroger/tmvl/P5Test.java)
```java
@RunWith(MultiClassLoaderJunitRunner.class)
@LibrarySets(
        librarySets = {
            "<snip>.../18.0/guava-18.0.jar,file:target/classes,file:target/test-classes",
            "<snip>.../19.0/guava-19.0.jar,file:target/classes,file:target/test-classes",
        }
)
class P5Test {
    @Test
    public void testGuavaAndCommonUser() {
        assertEquals("AAAA", GuavaAndCommonUser.toUpperCase("AAaa"));
    }
}
```

The Runner extends Suite and build the list of runner by looking at the LibrarySets annotation.

[P5MultiClassLoaderJunitRunner.java](src/main/java/io/github/arnaudroger/tmvl/P5MultiClassLoaderJunitRunner.java)
```java
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
```

the [P5ClassLoaderChangerRunner](src/main/java/io/github/arnaudroger/tmvl/P5ClassLoaderChangerRunner.java) just set the context class loader before running the test and restore it after.

[P5LibrarySetClassLoader](src/main/java/io/github/arnaudroger/tmvl/P5LibrarySetClassLoader.java) is the same as the ClassLoader in P4 except for doing the transform from String to URL.

```java
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
```

and Here you go bob is your uncle you have a declarative way to run your test against multiple version of the same library.

## What's next

Also it works, the management of the url can become cumbersome when you need to have more library in the classpath. It is possible
to give a list of class you want to be loaded from the classloader and write some code that will find where that class is loaded from.
You might also need to exclude some class from that class loader, surefire create a big jar of everything and if you do url discovery
it will pick that jar up and @Test will be in there. Then the runner won't recognize the @Test annotation on the class has they have different classe loader
so you will need to exclude any junit class.

## ClassLoader leakage

Some of the test might end up registering Class that reference the ClassLoader in a ThreadLocal making it reachable even
after the test run. You might have to make those class not being load by these ClassLoader - netty has some of those -.
To find those to a Heapdump load it in [MAT](http://www.eclipse.org/mat/), find your ClassLoader a look for path to gc root.

Some other classes might need to be exclude if you get ClassCastException.
If so you probably want to add a validation in the test that you are working with the right version of the library by
getting the ClassLoader of the class.



