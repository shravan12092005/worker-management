package com.workermanagement.data.entity

enum class Attendance { PRESENT, HALF_DAY, ABSENT }

enum class AdvanceTxnType { ADVANCE_GIVEN, DEDUCTION, WRITE_OFF }

enum class SettlementStatus { PENDING, PARTIALLY_PAID, PAID }

enum class PaymentMethod { CASH, OTHER }

enum class UserRole { CONTRACTOR, SITE_MANAGER }
