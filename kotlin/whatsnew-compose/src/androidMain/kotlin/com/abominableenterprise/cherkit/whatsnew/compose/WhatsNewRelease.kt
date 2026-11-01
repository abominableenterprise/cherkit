package com.abominableenterprise.cherkit.whatsnew.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** "Version 1.4.0" (a heading for TalkBack) with the release date, shown as-is, underneath. */
@Composable
internal fun WhatsNewReleaseHeader(title: String, date: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!date.isNullOrBlank()) {
            Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A release's bullets, one per line. Each bullet is its own TalkBack node; the "•" glyph is not read. */
@Composable
internal fun WhatsNewBulletList(bullets: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (bullet in bullets) {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(
                    "•",
                    Modifier.padding(end = 10.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(bullet, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The "You're on this version" pill: primary text on a light primary tint, wrapping at large font scales. */
@Composable
internal fun WhatsNewInstalledBadge(text: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        val style = MaterialTheme.typography.labelLarge
        val color = MaterialTheme.colorScheme.primary
        Text("✓", Modifier.padding(end = 6.dp).clearAndSetSemantics {}, style = style, color = color)
        Text(text, style = style, fontWeight = FontWeight.SemiBold, color = color)
    }
}
