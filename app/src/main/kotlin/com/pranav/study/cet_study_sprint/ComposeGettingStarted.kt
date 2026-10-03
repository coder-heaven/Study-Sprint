package com.pranav.study.cet_study_sprint

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// These are navigation destinations, not permission requests or one-time onboarding state.
private val homeShortcuts = listOf(
    "focus" to "Focus",
    "plan" to "Plan",
    "study" to "Study",
    "practice" to "Practice",
    "chat" to "Study buddy",
    "notes" to "Notes",
    "limits" to "App limits",
    "statistics" to "Progress"
)

/** Non-scrolling grid: the Home screen owns scrolling, including at large font sizes. */
@Composable
internal fun HomeShortcuts(go: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Explore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() })
        homeShortcuts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (route, label) ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp)
                            .testTag("home_action_$route").clickable { go(route) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeatureArtwork(when (route) {
                                "limits", "setup" -> R.drawable.art_protection_3d
                                "statistics", "leaderboard" -> R.drawable.art_progress_3d
                                "focus" -> R.drawable.art_focus_3d
                                "tutorial" -> R.drawable.ic_launcher
                                else -> R.drawable.art_study_3d
                            }, 40.dp)
                            Text(label, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Can be opened repeatedly; reading this guide never requests permissions or changes settings. */
@Composable
internal fun AppTutorialScreen(go: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .testTag("tutorial_steps"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Your Study Sprint guide",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Start with any step. You can return to this tutorial from Home whenever you need it. " +
                    "No permission or public sharing is required to read the guide or use local study features.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item {
            TutorialStep(
                "1. Make the app yours",
                "Choose your exam, class and exam date in Your profile. Setup explains optional device " +
                    "permissions; you can skip them and return when you want the related feature.",
                go,
                "profile" to "Edit your profile",
                "setup" to "Review optional setup"
            )
        }
        item {
            TutorialStep(
                "2. Focus on one task",
                "Open the focus timer, choose your session and break lengths, then start studying. " +
                    "If you choose to block distractions, review the selected apps before starting. " +
                    "An active focus lock cannot be bypassed by extra daily-limit time.",
                go,
                "focus" to "Open focus timer"
            )
        }
        item {
            TutorialStep(
                "3. Plan a manageable day",
                "Add a study task in Plan, choose what to work on next, and mark tasks complete as you finish. " +
                    "Small, specific tasks make it easier to get started.",
                go,
                "plan" to "Open study plan"
            )
        }
        item {
            TutorialStep(
                "4. Study, practise and import",
                "Use Study to explore your syllabus and keep chapter notes or PDFs. Open Practice for quizzes. " +
                    "Create / import MCQs lets you write questions or import a text-based PDF. " +
                    "Review imported questions and answers before saving and starting a quiz; scanned image-only PDFs may not import correctly.",
                go,
                "study" to "Open study & syllabus",
                "practice" to "Open practice quizzes",
                "mcq_editor" to "Create / import MCQs",
                "notes" to "Open notes & PDFs"
            )
        }
        item {
            TutorialStep(
                "5. Set daily app limits",
                "Open App limits and tap an app to edit its daily limit, including YouTube. " +
                    "Use YouTube normally until that editable daily limit is reached. After the limit, " +
                    "YouTube offers exactly two 5-minute bypasses per day, not unlimited extensions. " +
                    "Each bypass keeps counting down even if you leave YouTube. Daily usage and bypasses " +
                    "reset at midnight in your phone's time zone. Active focus locks cannot be bypassed.",
                go,
                "limits" to "Edit app & YouTube limits"
            )
        }
        item {
            TutorialStep(
                "6. Approve only the permissions you need",
                "Usage Access lets Study Sprint measure app usage for daily limits. Accessibility enables " +
                    "app blocking when you choose it. Both require your manual approval in Android settings; " +
                    "this tutorial cannot enable them. For a sideloaded APK, Android may ask you to allow " +
                    "restricted settings first. Notifications also require your approval when Android asks, " +
                    "and must be allowed for reminders and alerts. You can review these choices later; " +
                    "planning, notes and practice do not require Usage Access or Accessibility.",
                go,
                "setup" to "Review permission setup"
            )
        }
        item {
            TutorialStep(
                "7. Review progress; share only if you want",
                "Your progress brings together study activity and completed focus sessions. App usage shows " +
                    "device usage when Usage Access is available. The leaderboard is optional: only enable " +
                    "public sharing if you want your app name, photo and scores visible to others. Google " +
                    "sign-in is not required to participate. You can hide your profile later; public removal " +
                    "needs internet and the original account, and does not delete all retained score records. " +
                    "Read the Privacy Policy before deciding.",
                go,
                "statistics" to "View your progress",
                "app_usage" to "View app usage",
                "leaderboard" to "Review optional leaderboard",
                "privacy" to "Read Privacy Policy"
            )
        }
        item {
            TutorialStep(
                "8. Adjust settings and keep your data",
                "Use Settings for theme, focus goals and reminders. Normal APK updates installed over " +
                    "the existing app with the same package and signing signature preserve on-device data. " +
                    "Uninstalling the app or clearing its app data removes that local data. " +
                    "Google sign-in is not a backup or full sync of your notes, plans and other local study data. " +
                    "Keep separate copies of important material, and do not uninstall just to update.",
                go,
                "settings" to "Open settings",
                "home" to "Return to Home"
            )
        }
    }
}

@Composable
private fun TutorialStep(
    title: String,
    explanation: String,
    go: (String) -> Unit,
    vararg actions: Pair<String, String>
) {
    StudyCard {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (route, label) ->
                OutlinedButton(
                    onClick = { go(route) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(label, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
