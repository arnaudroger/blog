package io.github.arnaudroger.consumer.service;

import java.util.stream.Stream;

public interface DAO {
    Stream<String> stream();
}
