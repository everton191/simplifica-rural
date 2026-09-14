package br.com.simplificarural.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.simplificarural.ui.theme.RuralLightGreen

data class NavItem(val route: String, val label: String, val icon: ImageVector)

@Composable fun BottomNavigationBar(items: List<NavItem>, current: String, onSelect: (String) -> Unit) = NavigationBar(containerColor = Color.White) {
    items.forEach { item ->
        NavigationBarItem(selected = current == item.route, onClick = { onSelect(item.route) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = RuralLightGreen))
    }
}
