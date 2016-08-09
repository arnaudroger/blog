package io.github.arnaudroger.consumer.service;

public class ConsumerConsoleOutput {

    public static void main(String[] args) {
        SProducerService service = new SProducerService(new DFastDAO());
        service.produceStrings(System.out::println);
    }
}
