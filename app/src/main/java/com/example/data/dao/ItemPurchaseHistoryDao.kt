package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ItemPurchaseHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemPurchaseHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseHistory(history: ItemPurchaseHistory): Long

    @Query("SELECT * FROM item_purchase_history WHERE itemId = :itemId ORDER BY purchaseDate DESC")
    fun getPurchaseHistoryForItem(itemId: Int): Flow<List<ItemPurchaseHistory>>

    @Query("SELECT * FROM item_purchase_history WHERE itemId = :itemId ORDER BY purchaseDate DESC")
    suspend fun getPurchaseHistoryForItemSync(itemId: Int): List<ItemPurchaseHistory>

    @Query("SELECT * FROM item_purchase_history ORDER BY purchaseDate DESC")
    fun getAllPurchaseHistoryFlow(): Flow<List<ItemPurchaseHistory>>

    @Query("SELECT * FROM item_purchase_history ORDER BY purchaseDate DESC")
    suspend fun getAllPurchaseHistory(): List<ItemPurchaseHistory>

    @Query("SELECT * FROM item_purchase_history WHERE purchaseDate BETWEEN :start AND :end ORDER BY purchaseDate DESC")
    suspend fun getPurchaseHistoryInPeriod(start: Long, end: Long): List<ItemPurchaseHistory>

    @Query("DELETE FROM item_purchase_history WHERE itemId = :itemId")
    suspend fun deletePurchaseHistoryForItem(itemId: Int)
}
