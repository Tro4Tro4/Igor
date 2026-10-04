package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.StorageLocation
import org.junit.Assert.*
import org.junit.Test

class FoodCategoryTest {
    @Test fun `le nuove categorie conservano alimenti e luogo coerenti`() {
        assertTrue(FoodCategory.valueOf("SALUMI").isPerishable)
        assertTrue(FoodCategory.valueOf("FORMAGGI_FRESCHI").isSoldByWeight)
        assertTrue(FoodCategory.valueOf("FORMAGGI_STAGIONATI").isSoldByWeight)
        assertEquals(StorageLocation.FREEZER, FoodCategory.valueOf("GELATI").defaultLocation)
        assertEquals(StorageLocation.DISPENSA, FoodCategory.valueOf("BISCOTTI").defaultLocation)
        assertEquals(StorageLocation.DISPENSA, FoodCategory.valueOf("MERENDINE").defaultLocation)
        assertFalse(FoodCategory.valueOf("BUCATO").isFood)
    }
}
