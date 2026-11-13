package com.mansaketh.chitta.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mansaketh.chitta.core.design.components.ButtonStyle
import com.mansaketh.chitta.core.design.components.ChittaButton
import com.mansaketh.chitta.core.design.components.ChittaField
import com.mansaketh.chitta.core.design.components.HairlineRule
import com.mansaketh.chitta.core.design.icons.ChittaIcons
import com.mansaketh.chitta.core.design.theme.ChittaTheme
import com.mansaketh.chitta.core.design.tokens.Dimens
import com.mansaketh.chitta.core.domain.IconSearch
import com.mansaketh.chitta.core.model.Category
import com.mansaketh.chitta.ui.BottomOverlay

/** Pick the icon a category wears: a search box and the matching icons with their names. [onPick] gets null to go back to the plain tag. */
@Composable
fun IconPickerSheet(category: Category, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = ChittaTheme.colors
    var query by remember { mutableStateOf("") }
    val found = IconSearch.search(query, CategoryIconCatalog.choices)
    val current = IconSearch.keyOf(category.iconName)
    BottomOverlay(onDismiss) {
        Text("Icon for ${category.name}", style = ChittaTheme.type.title, color = c.ink)
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaField(value = query, onValueChange = { query = it }, label = "Search", singleLine = true, placeholder = "fuel, rent, doctor")
        if (found.isEmpty()) {
            Text("No icon matches \"$query\". Try another word.", style = ChittaTheme.type.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dimens.Gap4))
        } else {
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(found, key = { it.key }) { ch ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Dimens.RowMin).clickable(role = Role.Button, onClickLabel = "Use ${ch.label}") { onPick(ch.key) }.padding(vertical = Dimens.Gap2),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap3),
                    ) {
                        Icon(ChittaIcons.byPhosphorName[ch.key] ?: ChittaIcons.Tag, contentDescription = null, tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
                        Text(ch.label, style = if (ch.key == current) ChittaTheme.type.bodyMedium else ChittaTheme.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        if (ch.key == current) Icon(ChittaIcons.Check, contentDescription = "Selected", tint = c.ink, modifier = Modifier.size(Dimens.IconSize))
                    }
                    HairlineRule()
                }
            }
        }
        Spacer(Modifier.height(Dimens.Gap2))
        ChittaButton("Plain tag", { onPick(null) }, style = ButtonStyle.Quiet)
    }
}
