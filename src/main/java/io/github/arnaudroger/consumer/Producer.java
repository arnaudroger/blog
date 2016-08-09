package io.github.arnaudroger.consumer;

import java.util.function.Consumer;

@FunctionalInterface
public interface Producer<T> {
    void produce(Consumer<? super T> consumer);

    default Producer<T> compose(Producer<? extends T> composeWith) {
        return (consumer) -> {
            this.produce(consumer);
            composeWith.produce(consumer);
        };
    }
}
