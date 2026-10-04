package com.igor.fridge.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.igor.fridge.data.local.FoodCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryPresentationTest {
    @Test fun `ogni categoria si puo' presentare senza testo mancante`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        FoodCategory.entries.forEach {
            assertTrue(context.getString(it.labelRes()).isNotBlank())
            assertTrue(it.icon().isNotBlank())
        }
        assertEquals("Salumi e affettati", context.getString(FoodCategory.valueOf("SALUMI").labelRes()))
        assertEquals("Formaggi freschi", context.getString(FoodCategory.valueOf("FORMAGGI_FRESCHI").labelRes()))
    }
}
