package com.nexus.media.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.media.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(c: AppContainer, onDismiss: () -> Unit) {
    val eq by c.equalizer.state.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!eq.available) {
                Text(
                    "Equalizer aktif setelah ada lagu yang diputar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)
                )
                Button(onClick = { c.equalizer.attach(c.player.player.audioSessionId) }) { Text("Coba aktifkan") }
            } else {
                Text("Preset", Modifier.padding(top = 12.dp, bottom = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(eq.presets) { i, n -> AssistChip(onClick = { c.equalizer.usePreset(i) }, label = { Text(n) }) }
                }
                eq.levels.forEachIndexed { i, l ->
                    val hz = eq.freqsHz.getOrElse(i) { 0 }
                    Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Slider(
                        value = l.toFloat(), onValueChange = { c.equalizer.setBand(i, it.toInt()) },
                        valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat()
                    )
                }
                Text("Bass boost")
                Slider(value = eq.bass.toFloat(), onValueChange = { c.equalizer.setBass(it.toInt()) }, valueRange = 0f..1000f)
                Text("Surround (virtualizer)")
                Slider(value = eq.virt.toFloat(), onValueChange = { c.equalizer.setVirt(it.toInt()) }, valueRange = 0f..1000f)
                Text("Profil kustom", Modifier.padding(top = 8.dp, bottom = 4.dp))
                var name by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nama profil") }, modifier = Modifier.weight(1f))
                    Button(onClick = { if (name.isNotBlank()) { c.equalizer.saveProfile(name.trim()); name = "" } }) { Text("Simpan") }
                }
                LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(eq.profiles) { p ->
                        InputChip(
                            selected = false, onClick = { c.equalizer.loadProfile(p) }, label = { Text(p) },
                            trailingIcon = { Icon(Icons.Rounded.Close, "Hapus", Modifier.size(16.dp).clickable { c.equalizer.deleteProfile(p) }) }
                        )
                    }
                }
            }
        }
    }
}
