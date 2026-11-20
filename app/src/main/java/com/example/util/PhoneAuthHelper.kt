package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun normalizePhoneE164(raw: String): String {
    val trimmed = raw.trim().replace(Regex("[\\s\\-().]"), "")
    return if (trimmed.startsWith("00") && trimmed.length > 2) {
        "+${trimmed.drop(2)}"
    } else {
        trimmed
    }
}

fun isValidE164Phone(phone: String): Boolean {
    return Regex("^\\+[1-9]\\d{7,14}$").matches(phone)
}
