package uz.focuson.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.focuson.data.Store
import uz.focuson.data.Tag
import uz.focuson.data.TagColors

@Composable
fun TagsScreen(onBack: () -> Unit) {
    val tags by Store.tags.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    ScreenScaffold("Teglar", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "Teg — nima ustida ishlaganingiz. Statistikada shu bo'yicha ajratiladi.",
                color = C.muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(C.card)) {
                tags.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = t }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(t.color)))
                        Spacer(Modifier.width(14.dp))
                        Text(t.name, color = C.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { deleting = t }, enabled = tags.size > 1) {
                            Icon(Icons.Filled.Delete, "O'chirish", tint = if (tags.size > 1) C.muted else C.line)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { creating = true }, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Yangi teg")
            }
        }
    }

    if (creating || editing != null) {
        TagEditor(
            initial = editing,
            onSave = { name, color ->
                val e = editing
                if (e == null) {
                    Store.setTags(tags + Tag("t" + System.currentTimeMillis(), name, color))
                } else {
                    Store.setTags(tags.map { if (it.id == e.id) it.copy(name = name, color = color) else it })
                }
                creating = false; editing = null
            },
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("\"${t.name}\" o'chirilsinmi?") },
            text = { Text("Oldingi fokuslar saqlanadi, statistikada \"O'chirilgan teg\" bo'lib ko'rinadi.") },
            confirmButton = {
                TextButton(onClick = { Store.setTags(tags.filter { it.id != t.id }); deleting = null }) {
                    Text("O'chirish", color = C.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Bekor qilish") } },
        )
    }
}

@Composable
private fun TagEditor(initial: Tag?, onSave: (String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableLongStateOf(initial?.color ?: TagColors.all.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yangi teg" else "Tegni o'zgartirish") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) },
                    label = { Text("Nomi") }, singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
                Text("Rangi", color = C.muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                TagColors.all.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .border(if (c == color) 3.dp else 0.dp, C.ink, CircleShape)
                                    .clickable { color = c },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Saqlash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}
