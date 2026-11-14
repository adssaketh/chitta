package com.mansaketh.chitta.ui

import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.domain.LegacyCategoryIcons
import com.mansaketh.chitta.ui.manage.CategoryIconCatalog
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryIconsTest {
    @Test fun everyStandInIconIsDrawn() {
        val missing = LegacyCategoryIcons.allKeys.filter { ChittaIcons.byPhosphorName[it] == null }
        assertTrue(missing.isEmpty(), "mapped to an icon that is not drawn: $missing")
    }

    @Test fun everyPickerChoiceIsDrawnAndUnique() {
        val all = CategoryIconCatalog.allChoices
        assertTrue(all.map { it.key }.toSet().size == all.size, "an icon is listed twice")
        val missing = all.filter { ChittaIcons.byPhosphorName[it.key] == null }.map { it.key }
        assertTrue(missing.isEmpty(), "listed but not drawn: $missing")
    }

    @Test fun anImportedCategoryGetsAnIconFromItsPictureOrName() {
        assertNotNull(CategoryIconCatalog.imageFor("cutlery.png", "Dining"))
        assertNotNull(CategoryIconCatalog.imageFor("unknown-picture.png", "Transit"))
        assertNotNull(CategoryIconCatalog.imageFor(null, "Groceries"))
        assertTrue(CategoryIconCatalog.imageFor("unknown-picture.png", "Zzz") == null)
    }
}
