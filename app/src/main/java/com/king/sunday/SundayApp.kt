package com.king.sunday

import android.app.Application
import android.content.Context

class SundayApp : Application() {
    val core: SundayCore by lazy { SundayCore(this) }
}

val Context.sunday: SundayCore get() = (applicationContext as SundayApp).core
