package io.github.arnaudroger.consumer.service;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DFastDAO implements DAO {
    public Stream<String> stream() {
        return IntStream.range(0, 10).mapToObj(Integer::toString);
    }
}
