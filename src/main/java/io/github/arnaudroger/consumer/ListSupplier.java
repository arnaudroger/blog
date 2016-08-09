package io.github.arnaudroger.consumer;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface ListSupplier<T> {
    List<T> get();

    default ListSupplier<T> compose(ListSupplier<? extends T> composeWith) {
        return () -> {
            List<T> composedList = new ArrayList<>(get());
            composedList.addAll(composeWith.get());
            return composedList;
        };
    }
}
