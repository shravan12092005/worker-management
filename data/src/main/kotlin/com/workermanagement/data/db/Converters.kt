package com.workermanagement.data.db

import androidx.room.TypeConverter
import com.workermanagement.data.entity.Attendance
import com.workermanagement.data.entity.AdvanceTxnType
import com.workermanagement.data.entity.SettlementStatus
import com.workermanagement.data.entity.PaymentMethod
import com.workermanagement.data.entity.UserRole

/**
 * Room TypeConverters for all enum types.
 * Each enum is stored as its name string (TEXT in SQLite) so schema
 * changes to enum values are visible in the database without migrations.
 */
class Converters {

    @TypeConverter fun attendanceToString(v: Attendance): String = v.name
    @TypeConverter fun stringToAttendance(v: String): Attendance = Attendance.valueOf(v)

    @TypeConverter fun advanceTxnTypeToString(v: AdvanceTxnType): String = v.name
    @TypeConverter fun stringToAdvanceTxnType(v: String): AdvanceTxnType = AdvanceTxnType.valueOf(v)

    @TypeConverter fun settlementStatusToString(v: SettlementStatus): String = v.name
    @TypeConverter fun stringToSettlementStatus(v: String): SettlementStatus = SettlementStatus.valueOf(v)

    @TypeConverter fun paymentMethodToString(v: PaymentMethod): String = v.name
    @TypeConverter fun stringToPaymentMethod(v: String): PaymentMethod = PaymentMethod.valueOf(v)

    @TypeConverter fun userRoleToString(v: UserRole): String = v.name
    @TypeConverter fun stringToUserRole(v: String): UserRole = UserRole.valueOf(v)
}
