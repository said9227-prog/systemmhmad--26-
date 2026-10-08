package com.example.data.dao

import androidx.room.*
import com.example.data.model.ProductReturn
import com.example.data.model.ProductReturnItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductReturnDao {
    @Query("SELECT * FROM product_returns ORDER BY date DESC")
    fun getAllReturnsFlow(): Flow<List<ProductReturn>>

    @Query("SELECT * FROM product_returns ORDER BY date DESC")
    suspend fun getAllReturns(): List<ProductReturn>

    @Query("SELECT * FROM product_returns WHERE id = :id LIMIT 1")
    suspend fun getReturnById(id: Int): ProductReturn?

    @Query("SELECT * FROM product_returns WHERE id = :id LIMIT 1")
    fun getReturnByIdFlow(id: Int): Flow<ProductReturn?>

    @Query("SELECT * FROM product_returns WHERE type = :type ORDER BY date DESC")
    fun getReturnsByTypeFlow(type: String): Flow<List<ProductReturn>>

    @Query("SELECT * FROM product_returns WHERE clientId = :clientId ORDER BY date DESC")
    fun getReturnsByClientFlow(clientId: Int): Flow<List<ProductReturn>>

    @Query("SELECT * FROM product_returns WHERE clientId = :clientId ORDER BY date DESC")
    suspend fun getReturnsByClient(clientId: Int): List<ProductReturn>

    @Query("SELECT COALESCE(SUM(CASE WHEN convertedTotalAmount > 0 THEN convertedTotalAmount ELSE totalAmount END), 0.0) FROM product_returns WHERE clientId = :clientId AND settlementType != 'استرداد نقدي'")
    suspend fun getClientTotalReturnsCredited(clientId: Int): Double

    @Query("SELECT * FROM product_returns WHERE invoiceId = :invoiceId ORDER BY date DESC")
    fun getReturnsByInvoiceIdFlow(invoiceId: Int): Flow<List<ProductReturn>>

    @Query("SELECT * FROM product_returns WHERE invoiceId = :invoiceId ORDER BY date DESC")
    suspend fun getReturnsByInvoiceId(invoiceId: Int): List<ProductReturn>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(productReturn: ProductReturn): Long

    @Update
    suspend fun updateReturn(productReturn: ProductReturn)

    @Delete
    suspend fun deleteReturn(productReturn: ProductReturn)

    @Query("DELETE FROM product_returns WHERE id = :id")
    suspend fun deleteReturnById(id: Int)

    @Query("DELETE FROM product_returns")
    suspend fun deleteAllReturns()

    @Query("SELECT MAX(id) FROM product_returns")
    suspend fun getLastReturnId(): Int?

    // --- Return Items ---
    @Query("SELECT * FROM product_return_items WHERE returnId = :returnId")
    fun getReturnItemsFlow(returnId: Int): Flow<List<ProductReturnItem>>

    @Query("SELECT * FROM product_return_items WHERE returnId = :returnId")
    suspend fun getReturnItems(returnId: Int): List<ProductReturnItem>

    @Query("SELECT * FROM product_return_items")
    fun getAllReturnItemsFlow(): Flow<List<ProductReturnItem>>

    @Query("SELECT * FROM product_return_items")
    suspend fun getAllReturnItems(): List<ProductReturnItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnItem(item: ProductReturnItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReturnItems(items: List<ProductReturnItem>)

    @Query("DELETE FROM product_return_items WHERE returnId = :returnId")
    suspend fun deleteReturnItemsByReturnId(returnId: Int)

    @Query("DELETE FROM product_return_items")
    suspend fun deleteAllReturnItems()
}
