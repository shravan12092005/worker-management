package com.workermanagement.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workermanagement.data.entity.Payment

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(payment: Payment)

    @Query("SELECT * FROM payment WHERE settlement_id = :settlementId ORDER BY paid_on ASC")
    fun getForSettlement(settlementId: String): List<Payment>

    /**
     * Non-void payments for a settlement — used to compute amount paid (P-2, P-3).
     */
    @Query("""
        SELECT * FROM payment
        WHERE settlement_id = :settlementId AND is_void = 0
        ORDER BY paid_on ASC
    """)
    fun getNonVoidPaymentsForSettlement(settlementId: String): List<Payment>

    /**
     * Sum of non-void payments — used by settlementStatus (P-3).
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM payment
        WHERE settlement_id = :settlementId AND is_void = 0
    """)
    fun getTotalNonVoidPayments(settlementId: String): Int

    /**
     * Voids a payment (P-5). Amount is never edited; wrong payments are voided
     * and a new row is created. Both rows remain visible.
     */
    @Query("UPDATE payment SET is_void = 1 WHERE id = :id")
    fun voidPayment(id: String)
}
