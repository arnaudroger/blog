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

There are 2 effects at play there, one is the GC pressure impact, and the other one it the difference in byte reading.
let's write a small [jmh benchmark](https://github.com/arnaudroger/SimpleFlatMapper/blob/master/sfm-jmh/src/main/java/org/simpleflatmapper/csv/io/InputStreamBenchmark.java) that reads the content of a file using the different `InputStream`.

* `FileInputStream`
```java
try (FileInputStream is = new FileInputStream(file)) {
    consume(is, blackhole);
}
```
* `Files.newInputStream`
```java
try (InputStream reader = Files.newInputStream(file.toPath())) {
    consume(reader, blackhole);
}
```
* `RandomAccessFile`
```java
try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
    try (FileChannel open = randomAccessFile.getChannel()) {
        try (InputStream inputStream = Channels.newInputStream(open)) {
            consume(inputStream, blackhole);
        }
    }
}
```
* `FileChannel`
```java
        try (FileChannel open = FileChannel.open(file.toPath())) {
            try (InputStream is = Channels.newInputStream(open)) {
                consume(is, blackhole);
            }
}
```

And we will run that on a 16, 4k, 32k, 500 000 bytes, and 5 000 000 bytes file.

The full [results](/blog/assets/20170320-fileChannel-java8.xls).

If we plot the chart as different in % from the FileInputStream

![inputStream-perf](/blog/images/20170320-inputstream.png)

We can see that for small files 16, to 32k there are clear benefits in using the FileChannels, but as the size grows
the performance converges to FileInputStream, even getting slightly slower.

Also for big files, FileInputStream is better, Files.newInputStream gives far better results on small files and is pretty close
on big files.

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

Here we go for another [jmh benchmark](https://github.com/arnaudroger/SimpleFlatMapper/blob/master/sfm-jmh/src/main/java/org/simpleflatmapper/csv/io/ReaderBenchmark.java)
With the following strategies

* testFiles -> `Files.newBufferedReader`
* testFileChannelViaRandomFile -> `Channels.newReader(new RandomAccessFile(file, "r").getChannel())`
* testFileChannel -> `Channels.newReader(FileChannel.open(file.toPath()), "UTF-8")`
* testInputStreamReaderFromChannelInputStream -> `new InputStreamReader(Files.newInputStream(file.toPath()), "UTF-8")`
* testFileInputStream -> `new InputStreamReader(new FileInputStream(file), "UTF-8")`

We run the benchmark against a file with latin1 characters and one with Japanese characters.

Latin1 : 

![reader-perf-latin1](/blog/images/20170320-reader-latin1.png)

Japanese :

![reader-perf-latin1](/blog/images/20170320-reader-utf8.png)

And the winner is the testFileChannel strategy that is 30-40% faster on small file and equivalent in perf on big files.


# Summary

So it seems that for InputStream it can be worth moving to `Files.newInputStream`
and for Reader it is definitely worth using the `Channels.newReader(FileChannel.open(file.toPath()), "UTF-8")` strategy.

To go further it would be interesting to isolate what part of the performance difference is linked to the GC pressure 
and what part is linked to the difference in implementation.

The benchmark seems to be consistent between Ubuntu and MacOSX.

