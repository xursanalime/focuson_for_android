package uz.focuson.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.focuson.data.Catalog
import uz.focuson.data.Species
import uz.focuson.data.Store

@Composable
fun ShopScreen() {
    val coins by Store.coins.collectAsStateWithLifecycle()
    val owned by Store.owned.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<Species?>(null) }

    LazyVerticalGrid(
        GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Do'kon", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.weight(1f))
                    CoinIcon(20.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("$coins", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                }
                Text(
                    "Har bir o'sgan daqiqa = 1 tanga. Tangalarga yangi o'simliklar oling.",
                    color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(Catalog.species, key = { it.id }) { sp ->
            val has = sp.id in owned
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.card)
                    .clickable { pick = sp }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlantView(sp.id, 1f, modifier = Modifier.size(120.dp))
                Text(sp.name, color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                when {
                    sp.id == settings.speciesId -> Text("Tanlangan", color = C.primary, fontSize = 13.sp)
                    has -> Text("Sizda bor", color = C.muted, fontSize = 13.sp)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CoinIcon(15.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("${sp.price}", color = if (coins >= sp.price) C.ink else C.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    pick?.let { sp ->
        val has = sp.id in owned
        AlertDialog(
            onDismissRequest = { pick = null },
            icon = { PlantView(sp.id, 1f, modifier = Modifier.size(150.dp)) },
            title = { Text(sp.name) },
            text = {
                Text(
                    sp.about + "\n\n" + when {
                        has -> "Bu o'simlik sizda bor."
                        coins >= sp.price -> "Narxi: ${sp.price} tanga. Sizda: $coins."
                        else -> "Narxi: ${sp.price} tanga. Yana ${sp.price - coins} tanga kerak."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                when {
                    has -> TextButton(onClick = {
                        Store.updateSettings { it.copy(speciesId = sp.id) }; pick = null
                    }) { Text("Ekish uchun tanlash") }
                    coins >= sp.price -> TextButton(onClick = {
                        if (Store.buy(sp)) Store.updateSettings { it.copy(speciesId = sp.id) }
                        pick = null
                    }) { Text("Sotib olish") }
                    else -> TextButton(onClick = { pick = null }) { Text("Yaxshi") }
                }
            },
            dismissButton = { if (has || coins >= sp.price) TextButton(onClick = { pick = null }) { Text("Yopish", color = Color.Gray) } },
        )
    }
}
