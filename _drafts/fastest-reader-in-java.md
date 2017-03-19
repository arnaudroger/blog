---
layout: post
title: Revisiting File InputStream and Reader instantiation.
---

A few days ago I cam across a [tweet](https://twitter.com/leventov/status/842229472581435393?refsrc=email&s=11) from [@leventov](https://twitter.com/leventov) about that [Hadoop bug report](https://issues.apache.org/jira/browse/HDFS-8562).
Because `FileInputStream` implements a `finalize` method it creates quite a bit of pressure on the Garbage Collector.

# How do you avoid FileIntputStream

As stated in the bug report, you need to go through a [`FileChannel`](https://docs.oracle.com/javase/8/docs/api/index.html?java/nio/channels/FileChannel.html), then you can create an InputStream using [`Channels.newInputStrean(ch)`](https://docs.oracle.com/javase/8/docs/api/java/nio/channels/Channels.html#newInputStream-java.nio.channels.ReadableByteChannel-).
That's also what the convenience method [`Files.newInputStream`](https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html#newInputStream-java.nio.file.Path-java.nio.file.OpenOption...-) end up doing.

```java
try (FileChannel open = FileChannel.open(file.toPath())) {
    try (InputStream is = Channels.newInputStream(open)) {
      // do something
    }
}

try (InputStream is = Files.newInputStream(file.toPath)) {
// do something
}
```

If you are stuck in java 6 you will need to get the `FileChannel` via a [`RandomAccessFile`](https://docs.oracle.com/javase/8/docs/api/index.html?java/io/RandomAccessFile.html).

```java
try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
    try (FileChannel open = randomAccessFile.getChannel()) {
        try (InputStream is = Channels.newInputStream(open)) {
          // do something
        }
    }
}
```

Those will return a [`sun.nio.ch.ChannelInputStream`](https://github.com/dmlloyd/openjdk/blob/jdk8u/jdk8u/jdk/src/share/classes/sun/nio/ch/ChannelInputStream.java) which does not define a finalizer.

# which one is faster?

# What about Reader?

To instantiate a Reader without a `FileInputStream` we will use the FileChannel

```java 
try (FileChannel open = FileChannel.open(file.toPath())) {
    try (Reader reader = Channels.newReader(open, "UTF-8")) {
        // do something
    }
}
```

or in java6
```java
try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
    try (FileChannel open = randomAccessFile.getChannel()) {
        try (Reader reader = Channels.newReader(open, "UTF-8")) {
        // do something
        }
    }
}
```

You could also user an InputStreamReader on top of a ChannelInputStream.

```java
try (InputStream is = Files.newInputStream(file.toPath)) {
    try (Reader reader = new InputStreamReader(is, "UTF-8")) {
    // do something
    }
}
```

# which one is faster
   