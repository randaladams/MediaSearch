package com.example.mediasearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mediasearch.ui.theme.Brand

/** Colorful gradient top bar used on both screens. */
@Composable
fun GradientHeader(
    title: String,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brand.HeaderGradient)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = if (navigation == null) 20.dp else 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.OnHeader,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        below()
    }
}

/** Colored tile with the title's first letter (ignoring a leading "The"). */
@Composable
fun LetterAvatar(title: String, shape: Shape = RoundedCornerShape(12.dp)) {
    val initial = title.removePrefix("The ").trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '#'
    Box(
        Modifier.size(42.dp).clip(shape).background(Brand.avatarColor(title)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Small rounded label, e.g. a year or a season. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
