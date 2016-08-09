package io.github.arnaudroger.consumer.service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DSlowDAO implements DAO {

    public Stream<String> stream() {
        return IntStream.range(0, 10).mapToObj(i -> {
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            return Integer.toString(i);
        });
    }

}
