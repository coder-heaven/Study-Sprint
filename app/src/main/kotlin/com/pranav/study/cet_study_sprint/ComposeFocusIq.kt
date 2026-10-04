package com.pranav.study.cet_study_sprint

import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold)
)
private val base = Typography()
internal val FocusIqTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Poppins),
    displayMedium = base.displayMedium.copy(fontFamily = Poppins),
    displaySmall = base.displaySmall.copy(fontFamily = Poppins),
    headlineLarge = base.headlineLarge.copy(fontFamily = Poppins),
    headlineMedium = base.headlineMedium.copy(fontFamily = Poppins),
    headlineSmall = base.headlineSmall.copy(fontFamily = Poppins),
    titleLarge = base.titleLarge.copy(fontFamily = Poppins),
    titleMedium = base.titleMedium.copy(fontFamily = Poppins),
    titleSmall = base.titleSmall.copy(fontFamily = Poppins),
    bodyLarge = base.bodyLarge.copy(fontFamily = Poppins),
    bodyMedium = base.bodyMedium.copy(fontFamily = Poppins),
    bodySmall = base.bodySmall.copy(fontFamily = Poppins),
    labelLarge = base.labelLarge.copy(fontFamily = Poppins),
    labelMedium = base.labelMedium.copy(fontFamily = Poppins),
    labelSmall = base.labelSmall.copy(fontFamily = Poppins)
)

/** Native counterpart of the reference's emerald card shadows. */
internal fun Modifier.focusIqGlow() = shadow(5.dp, RoundedCornerShape(24.dp), clip = false,
    ambientColor = Color(0x2207AE88), spotColor = Color(0x3307AE88))

@Composable
internal fun FocusIqHeader(prefs: SharedPreferences, greeting: String, go: (String) -> Unit) {
    val name = prefs.getString("profile_name", "").orEmpty().trim().ifBlank { "Student" }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileAvatar(name, 46.dp) { go("profile") }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(greeting, fontSize = 12.sp, lineHeight = 16.sp)
            Text(name, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
        }

    }
}

@Composable
internal fun FocusIqButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth().focusIqGlow().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary) {
        Row(Modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.figma_play), null, Modifier.size(24.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun FocusIqTaskButton(onClick: () -> Unit) {
    Box(Modifier.width(98.dp).height(35.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxSize().shadow(8.dp, RoundedCornerShape(10.dp), clip = false,
            ambientColor = Color(0xFF07AE88), spotColor = Color(0xFF07AE88))
            .clickable(role = Role.Button, onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFF428273))) {
            Box(contentAlignment = Alignment.Center) { Text("View Task", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}


/** Decorative imagery stays outside functional controls and carries no duplicate accessibility label. */
@Composable
internal fun FeatureArtwork(resource: Int, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Image(painterResource(resource), contentDescription = null,
        modifier = modifier.size(size), contentScale = ContentScale.Fit)
}

internal fun headingArtwork(title: String): Int = when {
    title.contains("privacy", true) || title.contains("terms", true) ||
        title.contains("limit", true) || title.contains("protection", true) -> R.drawable.art_protection_3d
    title.contains("progress", true) || title.contains("history", true) ||
        title.contains("leaderboard", true) || title.contains("complete", true) -> R.drawable.art_progress_3d
    title.contains("focus", true) || title.contains("timer", true) -> R.drawable.art_focus_3d
    title.contains("profile", true) || title.contains("settings", true) ||
        title.contains("welcome", true) || title.contains("set up", true) -> R.drawable.study_sprint_logo_emerald
    else -> R.drawable.art_study_3d
}
