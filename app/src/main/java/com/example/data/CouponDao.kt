package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons ORDER BY createdAt DESC")
    fun getAllCoupons(): Flow<List<Coupon>>

    @Query("SELECT * FROM coupons WHERE id = :id LIMIT 1")
    fun getCouponById(id: Long): Flow<Coupon?>

    @Query("SELECT * FROM coupons WHERE id = :id LIMIT 1")
    suspend fun getCouponByIdOnce(id: Long): Coupon?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupon(coupon: Coupon): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupons(coupons: List<Coupon>): List<Long>

    @Update
    suspend fun updateCoupon(coupon: Coupon)

    @Delete
    suspend fun deleteCoupon(coupon: Coupon)

    @Query("DELETE FROM coupons WHERE id = :id")
    suspend fun deleteCouponById(id: Long)

    @Query("DELETE FROM coupons")
    suspend fun deleteAllCoupons()

    @Query("SELECT * FROM coupons")
    suspend fun getAllCouponsOnce(): List<Coupon>
}
