package com.dtr.scanner.data

object AttendanceDirection {
    const val IN = "IN"
    const val OUT = "OUT"

    fun isOut(value: String): Boolean = value.equals(OUT, ignoreCase = true)
}
