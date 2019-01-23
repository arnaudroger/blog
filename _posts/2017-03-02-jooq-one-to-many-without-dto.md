---
layout: post
title: jOOQ DTO-less one-to-many
---

This post is a follow-up to [Filling the one-to-many, many-to-many jOOQ mapping gap](/blog/2017/02/27/jooq-one-to-many.html)

In the previous post, we looked at an example of mapping one-to-many to a specific DTO, in this one we will look
at how to map one or more one-to-many without having to write any DTOs.

A word of warning before we proceed. 
The following code is not using exact matching, it is important to validate that the mapper is doing what you are expecting with a test. 
We will also use generic data structures to retrieve the information, it might not always be the best solution and creating your own DTO might be a better choice. 
 
## set the scene

For this post, we will use the schema setup in the [jOOQ-academy examples](https://github.com/jOOQ/jOOQ/tree/master/jOOQ-examples/jOOQ-academy)

```sql

CREATE TABLE author (
  id INT NOT NULL,
  first_name VARCHAR(50),
  last_name VARCHAR(50) NOT NULL,
  date_of_birth DATE,

  CONSTRAINT pk_t_author PRIMARY KEY (ID)
);

CREATE TABLE book (
  id INT NOT NULL,
  author_id INT NOT NULL,
  title VARCHAR(400) NOT NULL,
  published_in INT,

  rec_timestamp TIMESTAMP,

  CONSTRAINT pk_t_book PRIMARY KEY (id),
  CONSTRAINT fk_t_book_author_id FOREIGN KEY (author_id) REFERENCES author(id),
);

CREATE TABLE book_to_book_store (
  book_store_name VARCHAR(400) NOT NULL,
  book_id INTEGER NOT NULL,
  stock INTEGER,

  CONSTRAINT pk_b2bs PRIMARY KEY(book_store_name, book_id),
  CONSTRAINT fk_b2bs_bs_name FOREIGN KEY (book_store_name)
                             REFERENCES book_store (name)
                             ON DELETE CASCADE,
  CONSTRAINT fk_b2bs_b_id    FOREIGN KEY (book_id)
                             REFERENCES book (id)
                             ON DELETE CASCADE
);
```

## One-to-many

### the query

For the first query we will select the Authors and the Books they wrote 
with the following join query: 

```java
select(
        AUTHOR.ID, AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME, AUTHOR.DATE_OF_BIRTH,
        BOOK.ID, BOOK.TITLE)
    .from(AUTHOR)
        .leftJoin(BOOK).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
    .orderBy(AUTHOR.ID)
```

_UPDATE_ since [6.2.0](https://simpleflatmapper.org/2019/01/22/v6.2.0.html) a new option `unorderedJoin()` allow for join mapping with a unordered ResultSet.

that returns:

```
+----+----------+---------+-------------+----+------------+
|  ID|FIRST_NAME|LAST_NAME|DATE_OF_BIRTH|  ID|TITLE       |
+----+----------+---------+-------------+----+------------+
|   1|George    |Orwell   |1903-06-25   |   1|1984        |
|   1|George    |Orwell   |1903-06-25   |   2|Animal Farm |
|   2|Paulo     |Coelho   |1947-08-24   |   3|O Alquimista|
|   2|Paulo     |Coelho   |1947-08-24   |   4|Brida       |
```

### data structure

What we would like to have it mapped that to a structure like that: 
* George Orwell
  * 1984
  * Animal Farm
* Paulo Coelho
  * O Alquimista
  * Brida
  
to represent that structure we can use a `Tuple2` with the `AuthorRecord` as the first value
and a `List` of `BookRecord` as the second value.

The [jOOL project](https://github.com/jOOQ/jOOL) has its own Tuples implementation we can use, and the `Record` objects are generated.

The type will then be:
```java
Tuple2<AuthorRecord, List<BookRecord>>
```
### add the dependencies 

add to your pom

```xml
		<dependency>
			<groupId>org.jooq</groupId>
			<artifactId>jool</artifactId>
			<version>0.9.12</version>
		</dependency>

		<dependency>
			<groupId>org.simpleflatmapper</groupId>
			<artifactId>sfm-jdbc</artifactId>
			<version>3.11.2</version>
		</dependency>
```
 
### mapping

Now, all we have to do is instantiate a mapper on that type.
The id needs to be specified as a key, and we will need to use the [`TypeReference`](http://static.javadoc.io/org.simpleflatmapper/sfm-util/3.11.1/index.html?org/simpleflatmapper/util/TypeReference.html) object to capture the accurate generic type.

```java
JdbcMapper<Tuple2<AuthorRecord, List<BookRecord>>> mapper = 
    JdbcMapperFactory
        .newInstance()
        .addKeys("id")
        .newMapper(new TypeReference<Tuple2<AuthorRecord, List<BookRecord>>>() {});
```

### all of it together

Then we just need to execute the query, get the `ResultSet` and pass that to the `Mapper`.

```java
try (ResultSet rs =
             dsl
                .select(AUTHOR.ID, AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME, AUTHOR.DATE_OF_BIRTH,
                        BOOK.ID, BOOK.TITLE)
                .from(AUTHOR).leftJoin(BOOK).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
                .orderBy(AUTHOR.ID)
                .fetchResultSet()) {
    Stream<Tuple2<AuthorRecord, List<BookRecord>>> stream = mapper.stream(rs);

    // ... do something with the stream
}
```

## one-to-many-to-many

What if we want to add the bookstore information?

### query

With the following query
```java
select( AUTHOR.ID, AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME, AUTHOR.DATE_OF_BIRTH,
        BOOK.ID, BOOK.TITLE,
        BOOK_TO_BOOK_STORE.BOOK_STORE_NAME, BOOK_TO_BOOK_STORE.STOCK)
.from(AUTHOR)
.leftJoin(BOOK).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
.leftJoin(BOOK_TO_BOOK_STORE).on(BOOK_TO_BOOK_STORE.BOOK_ID.eq(BOOK.ID))
.orderBy(AUTHOR.ID)
```

that returns 
```
+----+----------+---------+-------------+----+------------+----------------+------+
|  ID|FIRST_NAME|LAST_NAME|DATE_OF_BIRTH|  ID|TITLE       |BOOK_STORE_NAME | STOCK|
+----+----------+---------+-------------+----+------------+----------------+------+
|   1|George    |Orwell   |1903-06-25   |   1|1984        |Amazon          |    10|
|   1|George    |Orwell   |1903-06-25   |   1|1984        |Barnes and Noble|     1|
|   1|George    |Orwell   |1903-06-25   |   2|Animal Farm |Amazon          |    10|
|   2|Paulo     |Coelho   |1947-08-24   |   3|O Alquimista|Amazon          |    10|
|   2|Paulo     |Coelho   |1947-08-24   |   3|O Alquimista|Barnes and Noble|     2|
|   2|Paulo     |Coelho   |1947-08-24   |   3|O Alquimista|Payot           |     1|
|   2|Paulo     |Coelho   |1947-08-24   |   4|Brida       |{null}          |{null}|
+----+----------+---------+-------------+----+------------+----------------+------+
```

### data structure

What we would like to have it mapped that to a structure like that: 
* George Orwell
  * 1984
    * Amazon, 10
    * Barnes and Noble, 1
  * Animal Farm
    * Amazon, 10
* Paulo Coelho
  * O Alquimista
    * Amazon, 10
    * Barnes and Noble, 2
    * Payot, 1
  * Brida

using `Tuple2` and `List` that would be:
```java
Tuple2<AuthorRecord,
        List<
            Tuple2<BookRecord, 
                List<BookToBookStoreRecord>>>
```

### the mapper

Let's create the `Mapper` for that, adding the keys:
```java
JdbcMapper<
        Tuple2<AuthorRecord,
                List<Tuple2<BookRecord, List<BookToBookStoreRecord>>>
            >
        > mapper =
            JdbcMapperFactory.newInstance()
                .addKeys("ID", "BOOK_STORE_NAME")
                .newMapper(
                    new TypeReference<
                        Tuple2<AuthorRecord, 
                            List<Tuple2<BookRecord, 
                                List<BookToBookStoreRecord>>>>>() {});
```


### all of it together

Now, same as before we `fetchResultSet` and pass it to the `Mapper`:

```java
try (ResultSet rs =
             DSL.using(connection())
                     .select(AUTHOR.ID, AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME, AUTHOR.DATE_OF_BIRTH,
                             BOOK.ID, BOOK.TITLE,
                             BOOK_TO_BOOK_STORE.BOOK_STORE_NAME, BOOK_TO_BOOK_STORE.STOCK)
                     .from(AUTHOR)
                        .leftJoin(BOOK).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
                        .leftJoin(BOOK_TO_BOOK_STORE).on(BOOK_TO_BOOK_STORE.BOOK_ID.eq(BOOK.ID))
                     .orderBy(AUTHOR.ID).fetchResultSet()) {
    Stream<
        Tuple2<AuthorRecord,
               List<Tuple2<BookRecord, List<BookToBookStoreRecord>>>
        >
    > stream = mapper.stream(rs);
    
        // ... do something with the stream

}
```

## Summary

Using SimpleFlatMapper you can easily map your one-to-many, many-to-many relationship
by only using Record objects Tuples and Lists.
Just add sfm-jdbc, create a Mapper to your Tuples and that's it.


## Resources

* [Samples](https://github.com/arnaudroger/SimpleFlatMapper/blob/master/sfm-examples/sfm-jooq/src/test/java/org/jooq/academy/sfm/Example_One_To_Many.java)
* [SimpleFlatMapper Joins](http://simpleflatmapper.org/0203-joins.html)
* [SimpleFlatMapper Property Mapping](http://simpleflatmapper.org/0201-property-mapping.html)
* [SimpleFlatMappper jOOQ integration](http://simpleflatmapper.org/0106-getting-started-jooq.html)
* [SimpleFlatMapper JDBC Mapper](http://simpleflatmapper.org/0102-getting-started-jdbc.html)

PS : Those examples will only work with version 3.11.2 or greater of SimpleFlatMapper, a few mapping [issues](https://github.com/arnaudroger/SimpleFlatMapper/milestone/117?closed=1) needed addressing
for it to work.