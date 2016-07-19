# How to write unit tests that run against multiple versions of a library

## Why?

When you are writing a library it might run against different versions of a dependency which you may want to support. 
The [DataStax](https://www.datastax.com), module for example was developed against Version 2.1.8 of the driver, but Version 3.0.3 has recently been released - and its behavior is slightly different.

On the code side I can use reflection to deal with the difference. For the test side though,
I started by creating a new maven module to test against the newer version, DataStax v3.0.3. 
The problem with this solution is that it introduces a duplication of test code and it's not very scalable.

Would it not be nice to be able to run the regular test against both version of the library?

## ClassLoader to the rescue

A servlet container needs to be able to load classes in isolation for each webapp, to do that each webapp has its own
classloader. A class loaded from 2 different ClassLoader are not Equals and a webapp cannot access class from the other webapp.
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

That code loads version 18 and 19 of the Ascii class and execute the toUpperCase on each.

```
asciiClass18.equals(asciiClass19) = false
18 - Ascii.toUpperCase('c') C
19 - Ascii.toUpperCase('c') C
```

The problem with that is that we need to use reflection to access the class methods
for that class. If we were to run compile code like :

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
If were to just call

```java
    GuavaUser.toLowerCase(System.getProperty("user.name"));

```

We can see that the Ascii class is loaded by the currentClassLoader not our isolated classLoader.
For GuavaUser to be linked to the specific version we also need to load it from the same ClassLoader.

If we just do:

```
urlClassLoader18.loadClass(GuavaUser.class.getName());
```

It will load GuavaUser from its parent class loader, which is currentClassLoader. 
We need to load GuavaUser from the UrlClassLoader by adding the location to the array of URL. But because
the classloader load from the parent ClassLoader we also need to pass null as the parent.

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

Then we get a different class loader for each GuavaUser class

```
io.github.arnaudroger.tmvl.GuavaUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
io.github.arnaudroger.tmvl.GuavaUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
```

Now what if we use a Class that also needs another dep?

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

we get

```
Caused by: java.lang.ClassNotFoundException: org.apache.commons.lang3.StringUtils
	at java.net.URLClassLoader.findClass(URLClassLoader.java:381)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:424)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
	... 11 more
```

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
org.apache.commons.lang3.StringUtils/classLoader = [file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.<snip>, file:/Users/aroger/dev/github/blog/target/classes/, file:/Users/aroger/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/.m2/repository/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar, file:/Applications/IntelliJ%20IDEA%202016.2%20CE%20EAP.app/Contents/lib/idea_rt.jar]
io.github.arnaudroger.tmvl.GuavaAndCommonUser/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
com.google.common.base.Ascii/classLoader = [http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/dev/github/blog/target/classes/]
org.apache.commons.lang3.StringUtils/classLoader = [file:/Library/Java/JavaVirtualMachines/jdk1.8.0_51.<snip>, file:/Users/aroger/dev/github/blog/target/classes/, file:/Users/aroger/.m2/repository/com/google/guava/guava/19.0/guava-19.0.jar, file:/Users/aroger/.m2/repository/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar, file:/Applications/IntelliJ%20IDEA%202016.2%20CE%20EAP.app/Contents/lib/idea_rt.jar]
```


## Junit Runner

Now that we have the code for our ClassLoader it would be nice to have that integrated in Junit.
So we can just annotate our test Class with the library dependencies needed and it creates the suite of test for each library set.

For example:

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

The Runner extends Suite and build the list of runners by looking at the LibrarySets annotation.

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

The [P5ClassLoaderChangerRunner](src/main/java/io/github/arnaudroger/tmvl/P5ClassLoaderChangerRunner.java) just set the context class loader before running the test and restore it after.

[P5LibrarySetClassLoader](src/main/java/io/github/arnaudroger/tmvl/P5LibrarySetClassLoader.java) is the same as the ClassLoader in P4 plus the transform from String to URL.

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

And there you go, bob's your uncle, we have a declarative way to run our tests against multiple versions of the same library.

## What's next

The management of the URLs can become cumbersome as your dependancies grow and you need to add more libraries in the classpath. 
It is possible to provide a list of classes you wish to load from the ClassLoader and write some code to find where each class is loaded from and add each to the list of URLs.

You may also need to exclude some classes from that ClassLoader - surefire creates a large jar of everything and if you perform URL discovery
it will pick that jar and @Test.class with it. Then the runner won't recognize the @Test annotation on the class as they have different ClassLoaders.
So ensure you avoid any junit classes being added to the URLs within the annotation.

## ClassLoader leakage

Some of the tests might end up registering Classes which reference the ClassLoader in a ThreadLocal, making it reachable even
after the test run. 
You might have exclude those Classes from the ClassLoader - netty has some of those.
To find those do a Heapdump and load it in [MAT](http://www.eclipse.org/mat/), find your ClassLoader and look for path to gc root.

Some other classes might need to be excluded if you get ClassCastException.
If so you probably want to add a validation check for the version of the library which the test is running against.



