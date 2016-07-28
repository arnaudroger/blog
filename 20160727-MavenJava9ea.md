# Maven building java 9-ea with jigsaw

If you don't have a module-info.java then you don't need to change anything.

## State of maven integration

[Maven - Java 9 Jigsaw](https://cwiki.apache.org/confluence/display/MAVEN/Java+9+-+Jigsaw)


### maven-compiler-plugin

The maven-compiler-plugin on the branch maven-compiler-plugin_jigsaw-ea integrates with javac 9.
There is no current release.

#### checkout

```
svn co http://svn.apache.org/repos/asf/maven/plugins/branches/maven-compiler-plugin_jigsaw-ea
```

### build

```
cd maven-compiler-plugin_jigsaw-ea
mvn clean install
```

you can now use the plugin from your local repo.

I also have a version built available by adding the following to your pom

```xml
<pluginRepositories>
    <pluginRepository>
        <id>arnaudroger-maven-plugin-repository</id>
        <url>https://arnaudroger.github.io/maven</url>
    </pluginRepository>
</pluginRepositories>
```

### profile

all you need to do now is to add a profile for jdk 9-ea with that plugin.
You will also need to overwrite the asm dependency to 6.0_ALPHA to support the new bytecode version.

```xml
   <profile>
        <id>jdk19</id>
        <activation>
            <jdk>9-ea</jdk>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.6-jigsaw-SNAPSHOT</version>
                    <configuration>
                        <source>9</source>
                        <target>9</target>
                    </configuration>
                    <dependencies>
                        <dependency>
                            <groupId>org.ow2.asm</groupId>
                            <artifactId>asm</artifactId>
                            <version>6.0_ALPHA</version>
                        </dependency>
                    </dependencies>
                </plugin>
            </plugins>
        </build>
    </profile>
```

## module-info.java

The jigsaw maven-compiler-plugin will compile will switch to the modular build if module-info.java is present.

You can find more info at [Jigsaw QuickStart](http://openjdk.java.net/projects/jigsaw/quick-start).
start with a empty one see what you need to import.

```
module mypackage {}
```

et Voila!

## Potential troubles

### ClassLoaders

The ClassLoader hierarchy is slightly changed. When you start your application you won't get an URLClassLoader. if your
code rely on that ... your will need to change it.

## third party library

Some third-party libraries depend on part of the jre that is no more accessible. You might need to add instruction
to export those when running the unit tests or compiling - for preprocessor, note that you will need to
then fork javac.

for the test
```xml
<profile>
    <id>jdk19</id>
    <activation>
        <jdk>9-ea</jdk>
    </activation>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <argLine>-XaddExports:java.base/sun.nio.ch=ALL-UNNAMED -XaddExports:jdk.unsupported/sun.misc=ALL-UNNAMED</argLine>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

for the javac

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.6-jigsaw-SNAPSHOT</version>
    <configuration>
        <source>9</source>
        <target>9</target>
        <fork>true</fork>
        <compilerArgs>
            <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
            <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED</arg>
        </compilerArgs>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm</artifactId>
            <version>6.0_ALPHA</version>
        </dependency>
    </dependencies>
</plugin>
```

you can verify which dependencies a jar has by using jdeps

```bash
jdeps ~/.m2/repository/io/netty/netty-all/4.0.39.Final/netty-all-4.0.39.Final.jar
```

### netty
You will need to export the following packages to run the code

* java.base/sun.nio.ch
* java.base/sun.security.util
* java.base/sun.security.x509
* jdk.unsupported/sun.misc

beware though that running in travis-ci it failed with the direct buffer, I had to deactivate the native epoll support.

```
-Dcassandra.native.epoll.enabled=false
```

### Lombok
Currently as of 1.16.10 Lombok still has issues with java9 as it relies on classes that are not present anymore.

To get the preprocessor as far as you can you will need to add the following to the maven-compiler-plugin configuration.

```
<compilerArgs>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
    <arg>-J-XaddExports:jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
</compilerArgs>
```


### Osgi bundle

BND seems to work pretty well, there is only 2 config to add.

 * _noee, it does not recognise the java version and can't generate the Require-Capabilities.
 * _failok, it fails on the module-info.class being at the root package.

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <extensions>true</extensions>
    <version>3.2.0</version>
    <configuration>
        <instructions>
            <_noee>true</_noee>
            <_failok>true</_failok>
        </instructions>
    </configuration>
</plugin>
```

