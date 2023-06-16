package me.nandroid.common

import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }
    val platformName = getPlatformName()
    var expaned by remember { mutableStateOf(false) }

    DropdownMenu(expaned, { expaned = expaned.not() }) {
        ResourceTypes.values().forEach { resourceType ->
            DropdownMenuItem(onClick = {}) {
                Text(text = resourceType.name)
            }
        }
    }
}
