---
layout: post
title: How to map a one-to-many with JdbcTemplate
---


I came across a stackoverflow [question](http://stackoverflow.com/questions/25280815/how-to-create-relationships-between-objects-in-spring-jdbc/43365102) that Vlad tweeted about.
How to map a one-to-many relationship in spring jdbcTemplate.

The first problem is that using the RowMapper interface can really only map a one-to-one relationship as it assumes one ResultSet row will map to one object.

as the [RowMapper](http://docs.spring.io/spring/docs/current/javadoc-api/org/springframework/jdbc/core/RowMapper.html) interface states:
 
> for mapping rows of a ResultSet on a per-row basis ...
> this interface perform the actual work of mapping each row to a result object

To be able to map a one-to-many that is typically recovered using a join, you need to be able to aggregate multiple rows into one object.
[ResultSetExtractor](http://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/ResultSetExtractor.html) will allow to work across the `ResultSet` and then return a Collection<T>.

> A RowMapper is usually a simpler choice for ResultSet processing, mapping one result object per row instead of one result object for the entire ResultSet.

You can use the root object id change to detect when a new object start.
The code would look like.

```java 
   List<User> users = new ArrayList<>();
   User currentUser = null;
   while(rs.next()) {
        long id = rs.getLong("id");
        if (currentUser == null) { // initial object
            currentUser = mapUser(rs);
        } else if (currentUser.getId() != id) { // break
            users.add(currentUser);
            currentUser = mapUser(rs);
        }
        currentUser.addRole(mapRole(rs));
   }
   if (currentUser != null) { // last object
        users.add(currentUser);        
   }
```

That is not too bad but become complexity increases quickly as you add more joins.

Fortunately, [SimpleFlatMapper](http://simpleflatmapper.org/) has already solved that problem.
All you need to do is create a `ResultSetExtractor` using the [JdbcTemplateMapperFactory](http://static.javadoc.io/org.simpleflatmapper/sfm-springjdbc/3.12/org/simpleflatmapper/jdbc/spring/JdbcTemplateMapperFactory.html).

```java
    private final ResultSetExtractor<List<User>> resultSetExtractor = 
        JdbcTemplateMapperFactory
            .newInstance()
            .addKeys("id") // the column name you expect the user id to be on
            .newResultSetExtractor(User.class);
```

and you can now just used this `resultSetExtractor` to map your one-to-many.

```java
   String query = 
        "SELECT u.id as id, u.username, u.id    as adverts_id, ad.text as adverts_text"
        + "FROM user u LEFT OUTER JOIN advert ad ON ad.account_id = ac.id order by id " 

    List<User> results = template.query(query, resultSetExtractor);
```

Note that sfm uses the root id break as the basis of its aggregation, the order of the query is therefore important.

## What if you want to keep the role list out of the user object?

Instead of having a User class and UserWithRole class you can just map the query to a `Tuple2<User, List<Role>>` using sfm-tuples or [jOOL](https://github.com/jOOQ/jOOL).

```java
    private final ResultSetExtractor<Tuple2<User, List<Role>>> resultSetExtractor = 
        JdbcTemplateMapperFactory
            .newInstance()
            .addKeys("id") // the column name you expect the user id to be on
            .newResultSetExtractor(new TypeReference<Tuple2<User, List<Role>>> {});
```

and then 

```java
   String query = 
        "SELECT u.id as id, u.username, u.id    as adverts_id, ad.text as adverts_text"
        + "FROM user u LEFT OUTER JOIN advert ad ON ad.account_id = ac.id order by id " 

    List<Tuple2<User, List<Role>>> results = template.query(query, resultSetExtractor);
```

each Tuple2 has the User and its associated Roles.


## Summary

You don't need to use a complex ORM to map your sql query to object, even with multiple one-to-many relationships.
SimpleFlatMapper already deals with all the complexity for you, so drop writing manual RowMapper and spend your time writing business logic not boilerplate mapping code.



