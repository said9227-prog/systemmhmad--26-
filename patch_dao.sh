#!/bin/bash
sed -i '/fun searchItems(query: String): Flow<List<Item>>/a \
    @Query("SELECT * FROM items WHERE name LIKE :query LIMIT 50")\
    suspend fun autocompleteItems(query: String): List<Item>\
\
    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE LIMIT 1")\
    suspend fun getItemByName(name: String): Item?\
' app/src/main/java/com/example/data/dao/Daos.kt
