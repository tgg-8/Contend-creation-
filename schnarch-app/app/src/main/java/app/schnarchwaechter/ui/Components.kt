package app.schnarchwaechter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.schnarchwaechter.ui.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = AppColors.Primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface, contentColor = AppColors.Text),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueColor: Color = AppColors.Text) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = AppColors.Surface) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = AppColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = AppColors.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text, color = AppColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Surface(Modifier.size(size.dp), shape = CircleShape, color = color) {}
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.Muted, fontSize = 12.sp)
    }
}

@Composable
fun BodyText(text: String, color: Color = AppColors.Text) {
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun Disclaimer() {
    Text(
        "Diese App ist kein Medizinprodukt und stellt keine Diagnose. Wenn dir etwas Sorgen macht " +
            "(z. B. häufige Atempausen, starke Tagesmüdigkeit), sprich bitte mit einer Ärztin oder einem Arzt.",
        color = AppColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp),
    )
}

/**
 * Current time that only updates once per minute and only while the screen is visible
 * (stops completely when the screen is off -> no wake-ups for the UI).
 */
@Composable
fun rememberMinuteTicker(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 50)
            }
        }
    }
    return now
}
