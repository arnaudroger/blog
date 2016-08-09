package io.github.arnaudroger.consumer.service;

import java.util.List;
import java.util.stream.Collectors;

public class SClassicService {

    private final DAO dao;

    public SClassicService(DAO dao) {
        this.dao = dao;
    }

    public List<String> getStrings() {
        return dao.stream().collect(Collectors.toList());
    }
}
