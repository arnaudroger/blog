# Why you should replace your return List<T> with a Consumer<T> parameter

It is common practice to have your Service layer, Dao layer etc... 
return a List or Collection of objects as a result of the call.

But I will argue that if instead of

```java
 List<T> getAllMyTs()
```

we uses

```java
 produceAllMyTs(Consumer<T> consumer)
```

we end up with more scalable and easier to maintain code.

## A tale of 2 services
 
In our Classic Service 

```java
public class SClassicService {

    private final DAO dao;

    public SClassicService(DAO dao) {
        this.dao = dao;
    }

    public List<String> getStrings() {
        return dao.stream().collect(Collectors.toList());
    }
}
```

we return a List<String> that we get from a stream return by our DAO. We make it the
responsibility of the service to choose an appropriate data structure to store the result.

In our Producer Service

```java
public class SProducerService {
    private final DAO dao;

    public SProducerService(DAO dao) {
        this.dao = dao;
    }

    public void produceStrings(Consumer<? super String> consumer) {
        dao.stream().forEach(consumer);
    }

}
```

we use the Consumer interface as a way to communicate each element. 
It's the responsibility of the caller to decide what to do with it. 
If we don't need to have a List we won't need to create one - when do we really need? -.

For small amount of data it may not matter, but if you fetching a few 100 000s and don't need to 
store them in a list it can make a big difference.

Let's imagine a simple class that uses the service and output it to the console.

in Classic world 
```java
service.getStrings().forEach(System.out::println);
```

in the Producer 
```java
service.produceStrings(System.out::println);
```

In the classic world we won't start printing the data until 
they have all been put in the list.

So if we have use [DSlowDAO](src/main/java/io/github/arnaudroger/consumer/service/DSlowDAO.java) nothing happens for a while and then all of a sudden we display all
the data.

If the Consumer was slow, we would fetch all the data and keep them all in memory until the last is consumed.


In the producer world only the one currently being processed is alive. If the producer is slow 
we will output the value as soon as they arrive. If the consumer is slow we will not
produce the next value until the consumer is ready to consume it.
Those are great properties to have in your system. 


## A tale of 2 functional interfaces

The Consumer approach is also easier to compose.

When you use a Supplier 
```java
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
```

you will need to recreate a new list and add the list from the 
2 suppliers. That's a total of 3 Lists being created.


But using a producer

```java
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
```

you just need to call on the first producer and then on the second.
no more garbage created because it is not the responsibility of the producer
to create the data structure to store the objects.


## What if the consumer needs a list?

Just do 

```java
List<String> list = new ArrayList<>;
service.produceStrings(list:add);
```

you could also make the producer return the consumer 

```java
public <C extends Consumer<String>> produceString(C consumer) {
    ...
    return consumer;
}
```

and create a 
```java
class ToListConsumer<T> implements Consumer<T> {
    List<T> list = new ArrayList<>();
    
    void accept(T t) {
        list.add(t);
    }
    
    List<T> get() {
        return list;
    }
}

```

you can now do 
```java
List<String> list = service.produceStrings(new ToListConsumer<String>()).get();
```