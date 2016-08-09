package io.github.arnaudroger.consumer.service;

public class ClassicConsoleOutput {
    public static void main(String[] args) {
        SClassicService service = new SClassicService(new DFastDAO());
        service.getStrings().forEach(System.out::println);
    }
}
