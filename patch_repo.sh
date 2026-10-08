#!/bin/bash
sed -i '/fun searchItems(query: String): Flow<List<Item>> = itemDao.searchItems("%$query%")/a \
    suspend fun autocompleteItems(query: String): List<Item> = withContext(Dispatchers.IO) { itemDao.autocompleteItems("%$query%") }\
    suspend fun getItemByName(name: String): Item? = withContext(Dispatchers.IO) { itemDao.getItemByName(name) }' app/src/main/java/com/example/data/repository/AppRepository.kt
