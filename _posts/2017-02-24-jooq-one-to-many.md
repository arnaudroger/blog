---
layout: post
date: 2017-02-27
title: Filling the one-to-many, many-to-many jOOQ mapping gap
draft: false
---

A few years ago Marco Behler wrote [JAVA PERSISTENCE GHETTO (AND HOW JOOQ MIGHT CHANGE THAT)](https://www.marcobehler.com/2014/07/06/the-java-persistence-ghetto-and-how-jooq-might-change-that-2/)
talking about jOOQ strength and weaknesses. 

One of the pain points he raised was many to many mapping:

> 2. HOW DO I CONVERT BETWEEN DATABASE <--> OBJECTS?
>  
> After or before querying the database, the next question is: How do we go from database to objects and vice versa. And Hibernate et. al are really strong here. jOOQ has active records and yep, you can plug in stuff like objectmodelmapper, but mapping with jOOQ does not yet give you this "wow-effect" and feels clumsy at time. Try to get a more complex many-to-many relationship mapped and sooner or later you'll end up writing some query DTOs and  continue mapping to other objects. This is actually one of our main gripes with jOOQ at the moment.

As Lukas answered it is a deliberate choice, see [Issue 1530](https://github.com/jOOQ/jOOQ/issues/1530). By design jOOQ is 
very open to other [mappers](https://www.jooq.org/doc/3.9/manual/sql-execution/fetching/pojos-with-recordmapper-provider/) allowing 
other libraries to solves those problems.

In this post, I will see how [SimpleFlatMapper](http://simpleflatmapper.org/) can help filling jOOQ mapping gaps.

## StackOverflow

In this [StackOverflow question](http://stackoverflow.com/questions/23329127/jooq-pojos-with-one-to-many-and-many-to-many-relations) a jOOQ user
ask about the possibility of mapping a many-to-many in the following class

```java 
public class Location {
    private final String name;
    private UUID player;
    private List<UUID> invitedPlayers;
```

And a schema with 3 tables `Location`, `Player`, and `location2player` many-to-many join table between the first 2.

jOOQ has some mapping functionality but it expects one object per row. It is therefore not possible 
to return one Location object with a List of invited player with the RecordMapper. If a `Location` has 3 invited player
all we can get is 3 identical Location object with 1 invited player each.

## SimpleFlatMapper to the rescue

[SimpleFlatMapper](http://simpleflatmapper.org/) can integrate with [jOOQ](http://simpleflatmapper.org/0106-getting-started-jooq.html) but as stated earlier it is not possible to map multiple rows with one object with the jOOQ `RecordMapper` integration.
[sfm-jdbc](http://simpleflatmapper.org/0102-getting-started-jdbc.html) though can work at the `ResultSet` level and aggregate the [join](http://simpleflatmapper.org/0203-joins.html) into the Location object.
Fortunately, jOOQ provide access to the underlying ResultSet, so all we need to do is instantiate a Sfm JdbcMapper and we will be sorted.

## Add [sfm-jdbc](http://search.maven.org/#artifactdetails%7Corg.simpleflatmapper%7Csfm-jdbc%7C3.11.1%7C) as dependency 

```xml
<dependency>
    <groupId>org.simpleflatmapper</groupId>
    <artifactId>sfm-jdbc</artifactId>
    <version>3.11.2</version>
</dependency>
```
 
## Instantiate the JdbcMapper

For join aggregation, Sfm needs to know what are the column representing the id of the object.

assuming the following SQL Query would return the following fields, `player` is the id of the root object. 

```sql
p.player-id as player, l.name as name, l1p.player-id as invited_players_player
```

to create the JdbcMapper just write the following code:

```java
JdbcMapper<Location> jdbcMapper = 
    JdbcMapperFactory.addKeys("player").newMapper(Location.class);
```

The `Mapper` is thread-safe it is recommended to have only one instance per type, or type - columns for [static mapper](http://simpleflatmapper.org/0102-getting-started-jdbc.html#static-mapping).

## Execute your sqlQuery with jOOQ

For the break detection on the root object you need to order by the id of the root object, `.orderBy(LOCATION.PLAYER_ID)` here.
Now we just need to execute the query, retrieve the `ResultSet` and use the jdbcMapper to map the rows to `Location` object.

_UPDATE_ since [6.2.0](https://simpleflatmapper.org/2019/01/22/v6.2.0.html) a new option `unorderedJoin()` allow for join mapping with a unordered ResultSet.

```java
try (ResultSet rs = 
        dsl
            .select(
                    LOCATION.NAME.as("name"), 
                    LOCATION.PLAYER_ID.as("player"), 
                    LOCATION2PLAYER.PLAYERID.as("invited_players_player"))
            .from(LOCATION)
                .leftOuterJoin(LOCATION2PLAYER)
                    .on(LOCATION2PLAYER.LOCATION_ID.eq(LOCATION.LOCATION_ID))
            .orderBy(LOCATION.PLAYER_ID)
            .fetchResultSet()) { 
    Stream<Location> stream = jdbcMapper.stream(rs);
    
    // do something on the stream.
}
```

and here you go you will have a Stream of Location as describes in the original question. Simple.

More information :
* [SimpleFlatMapper Joins](http://simpleflatmapper.org/0203-joins.html)
* [SimpleFlatMapper Property Mapping](http://simpleflatmapper.org/0201-property-mapping.html)
* [SimpleFlatMappper jOOQ integration](http://simpleflatmapper.org/0106-getting-started-jooq.html)
* [SimpleFlatMapper JDBC Mapper](http://simpleflatmapper.org/0102-getting-started-jdbc.html)

PS: It should be possible to do all that without having to write any DTO, blog post to come.
PS2: here it is [jOOQ DTO-less one-to-many](/blog/2017/03/02/jooq-one-to-many-without-dto.html)