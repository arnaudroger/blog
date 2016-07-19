package io.github.arnaudroger.tmvl;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(P5MultiClassLoaderJunitRunner.class)
@P5LibrarySets(
        librarySets = {
                "http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar,file:target/classes,file:target/test-classes",
                "http://repo1.maven.org/maven2/com/google/guava/guava/19.0/guava-19.0.jar,file:target/classes,file:target/test-classes",
        }
)
public class P5Test {
    @Test
    public void testGuavaAndCommonUser() {
        assertEquals("AAAA", GuavaAndCommonUser.toLowerCase("AAaa"));
    }
}