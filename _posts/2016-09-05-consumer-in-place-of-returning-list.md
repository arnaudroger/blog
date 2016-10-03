---
layout: post
date: 2016-09-19
---
# Why you should replace your return List<T> with a Consumer<T> parameter

It is common practice to have your Service layer, Dao layer etc... 
return a List<T> or Collection<T> as a result of the call.

{% highlight java %}
 List<T> getAllMyTs()
{% endhighlight %}

but with the introduction of lambdas in java 8, the use of callback has become far cheaper
and user a Consumer<T> as a parameter 

{% highlight java %}
 produceAllMyTs(Consumer<T> consumer)
{% endhighlight %}

could make your code less couple, easier to maintain and more scalable.

## A tale of 2 services
 
In our Classic Service 

{% highlight java %}
public class SClassicService {

    public List<String> getStrings() {
        // ...
    }
}
{% endhighlight %}

we return a List<String> that we get from our DAO. We make it the
responsibility of the service to choose an appropriate data structure to store the result, 
ArrayList or LinkedList?, ImmutableList?

In our Producer Service

{% highlight java %}
public class SProducerService {

    public void produceStrings(Consumer<? super String> consumer) {
        // ...
    }

}
{% endhighlight %}

we use the Consumer interface as a way to communicate each element. 
It's the responsibility of the caller to decide what to do with it. 
If we don't need to have a List we won't need to create one - when do we need it? some framework push you towards that but there is always a way to do without -.

For small amount of data it may not matter, but if you fetching a few 100 000s and don't need to 
store them in a list it can make a big difference.

Let's imagine a simple class that uses the service and output it to the console.

in Classic world 
{% highlight java %}
service.getStrings().forEach(System.out::println);
{% endhighlight %}

in the Producer 
{% highlight java %}
service.produceStrings(System.out::println);
{% endhighlight %}

In the classic world we won't start printing the data until 
they have all been put in the list.

So if we have use [DSlowDAO](https://github.com/arnaudroger/blog/tree/master/src/main/java/io/github/arnaudroger/consumer/service/DSlowDAO.java) nothing happens for a while and then all of a sudden we display all
the data.

If the Consumer was slow, we would fetch all the data and keep them all in memory until the last is consumed.

In the producer world only the one currently being processed is alive. If the producer is slow 
we will output the value as soon as they arrive. If the consumer is slow we will not
produce the next value until the consumer is ready to consume it.
Those are great properties to have in your system. 

## A tale of 2 functional interfaces

The Consumer approach is also easier to compose.

When you use a Supplier 
{% highlight java %}
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
{% endhighlight %}

you will need to create 3 Lists.

But using a producer

{% highlight java %}
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
{% endhighlight %}

you just need to call on the first producer then the second.
no more garbage created because it is not the responsibility of the producer
to create the data structure to store the objects.


## What if the consumer needs a list?

You can just use the List as a consumer

{% highlight java %}
List<String> list = new ArrayList<>;
service.produceStrings(list:add);
{% endhighlight %}

you could also make the producer return the consumer with the actual type using the following generic signature

{% highlight java %}
public <C extends Consumer<String>> produceString(C consumer) {
    ...
    return consumer;
}
{% endhighlight %}

then create generic ToListConsumer 

{% highlight java %}
class ToListConsumer<T> implements Consumer<T> {
    public static <T> ToListConsumer<T> toList() {
        return new ToListConsumer<T>();
    }

    List<T> list = new ArrayList<>();
    
    void accept(T t) {
        list.add(t);
    }
    
    List<T> get() {
        return list;
    }
}

{% endhighlight %}

you can now chain the consumer call with the list fetching. 
{% highlight java %}
List<String> list = service.produceStrings(ToListConsumer.toList()).get();
{% endhighlight %}

## My DAO layer return a List?

There is still some benefits if you can't remove the List creation. The consumer is called 
inside the transaction, no need for the dreadful open session in view filter.

Unfortunately JPA does not seem to support ScrollablResults but Hibernate does so if you use
that implementation you can change your code to fetch only a few items at a time.

If you use spring jdbc then just use the RowCallBackHandler, map the object and callback the consumer.

{% highlight java %}
template.query(sql, rs -> consumer.apply(rowMapper.map(rs)));
{% endhighlight %}

jOOQ and SimpleFlatMapper already support a consumer callback.

{% highlight java %}
// jooq
jooqQuery.fetchInto(System.out::println);
jooqQuery.fetchStream().forEach(System.out::println);

// sfm
mapper.forEach(resultSet, System.out::println);
mapper.stream(resultSet).forEach(System.out::println);
{% endhighlight %}

## Using that pattern even when it return a single Entry?

The consumer can be use with 0 to n number of elements.
It's a good alternative to returning Optional<T>.

{% highlight java %}
service.getOptionalValue().ifPresent(consumer);
{% endhighlight %}

becomes

{% highlight java %}
service.produceOptionalValue(consumer);
{% endhighlight %}

## Conclusion

Using a consumer allow you to build more flexible and more reactive interface without changing your design too much. 
It's very straightforward and can help reduce the coupling of your application. There is nothing new there but
java8 with the addition of Lambdas made it a lot cheaper on the dev side. It's time to get back to message passing.




