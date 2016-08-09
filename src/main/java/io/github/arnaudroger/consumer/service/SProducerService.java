package io.github.arnaudroger.consumer.service;

import java.util.function.Consumer;

public class SProducerService {
    private final DAO dao;

    public SProducerService(DAO dao) {
        this.dao = dao;
    }

    public void produceStrings(Consumer<? super String> consumer) {
        dao.stream().forEach(consumer);
    }

}
