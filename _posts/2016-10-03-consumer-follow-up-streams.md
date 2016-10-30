---
layout: post
date: 2016-10-03
draft: true
---

In my previous [post](/blog/2016/09/19/consumer-in-place-of-returning-list.html) I talked about replacing
return List<T> with a Consumer<T>. There was a few comments on why not returning Stream. But those 
solution can cause resource leak if the Stream is not close.

[s888marks](https://www.reddit.com/user/s888marks) pointed us to a solution that would solve the side effect and resource scoping issue 
used in the  new [StackWalker API](http://download.java.net/java/jdk9/docs/api/java/lang/StackWalker.html#walk-java.util.function.Function-).

{% highlight java %}
<T, R> R getItems(Function<? super Stream<T>, ? extends R> f)
{% endhighlight %}

In that follow up post I'll go through the code necessary to implement a classic list return, the function Stream and the Consumer.



