package app.otakureader.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.components.HankoBadge
import app.otakureader.domain.model.Achievement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AchievementsSection(
    achievements: List<Achievement>,
    modifier: Modifier = Modifier
) {
    if (achievements.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.achievements_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(achievements) { achievement ->
                AchievementItem(achievement = achievement)
            }
        }
    }
}

@Composable
private fun AchievementItem(achievement: Achievement) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (achievement.isUnlocked) 1f else 0.4f)
    ) {
        HankoBadge(count = if (achievement.isUnlocked) 1 else 0)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = achievement.definition.name
                .lowercase()
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2
        )
        Text(
            text = if (achievement.isUnlocked) {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(achievement.unlockedAt))
            } else {
                "${achievement.progress}/${achievement.target}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
