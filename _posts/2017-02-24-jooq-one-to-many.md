---
layout: post
date: 2017-02-24
draft: true
---

Marco Behler wrote a post [JAVA PERSISTENCE GHETTO (AND HOW JOOQ MIGHT CHANGE THAT)](https://www.marcobehler.com/2014/07/06/the-java-persistence-ghetto-and-how-jooq-might-change-that-2/)
talking about jOOQ strength and weaknesses. 

The main pain point seems to be many to many mapping 

> 2. HOW DO I CONVERT BETWEEN DATABASE <--> OBJECTS?
>  
> After or before querying the database, the next question is: How do we go from database to objects and vice versa. And Hibernate et. al are really strong here. jOOQ has active records and yep, you can plug in stuff like objectmodelmapper, but mapping with jOOQ does not yet give you this "wow-effect" and feels clumsy at time. Try to get a more complex many-to-many relationship mapped and sooner or later you'll end up writing some query DTOs and  continue mapping to other objects. This is actually one of our main gripes with jOOQ at the moment.

In this post, I will talk how [SimpleFlatMapper](http://simpleflatmapper.org/) can help solving those issues.

## StackOverflow

Let's have a look at this [question](http://stackoverflow.com/questions/23329127/jooq-pojos-with-one-to-many-and-many-to-many-relations)
posted 2 years ago.

The class is as follow 

```java 
public class Location {
    private final String name;
    private UUID player;
    private List<UUID> invitedPlayers;
```

with 4 tables Location, Player, and a many-to-many join table between those 2.
jOOQ has some mapping functionality but it expects one object per row. It is now possible 
to aggregate in jOOQ as Lukas reply. And even with the RecordMapper, you can't really solve that problem.

## SimpleFlatMapper to the rescue.

[SimpleFlatMapper](http://simpleflatmapper.org/) has [jOOQ integration](http://simpleflatmapper.org/0106-getting-started-jooq.html)
 but as stated in the doc it is not possible to aggregate object in with the jOOQ record mapper integration.
 
 Fortunately, jOOQ provide access to the underlying ResultSet, so all we need to do is
 instantiate a Sfm JdbcMapper and we will be sorted
 
## Add [sfm-jdbc](http://search.maven.org/#artifactdetails|org.simpleflatmapper|sfm-jdbc|3.11.1|) as dependency

```xml
<dependency>
    <groupId>org.simpleflatmapper</groupId>
    <artifactId>sfm-jdbc</artifactId>
    <version>3.11.1</version>
</dependency>
```
 
## Instantiate the JdbcMapper

For join aggregation, Sfm needs to know what are the column representing the id of the object.

assuming the SQL Query would return the following fields

```sql
p.player-id as player, l.name as name, i.player-id as invitedPlayers_id
```


```java
JdbcMapper<Location> jdbcMapper = 
    JdbcMapperFactory.addKeys("player").newMapper(Location.class);
```

## Execute your sqlQuery with jOOQ

all that's left is to execute the query, retrieve the `ResultSet` and use the jdbcMapper to fetch the result.
I will skip on the resource closing code.

```java

ResultSet rs = dsl.select ... .fetchResultSet();

Stream<Location> stream = jdbcMapper.stream(rs);

```

and here you go you will have a Stream of Location as describes in the original question.

More information about [Sfm Joins](http://simpleflatmapper.org/0203-joins.html).