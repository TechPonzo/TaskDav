package app.taskdav.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.taskdav.R
import app.taskdav.data.LanguagePreference
import app.taskdav.ui.theme.TaskDavRadii
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

@Composable
fun OnboardingScreen(
    language: LanguagePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
    onFinished: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(Icons.Filled.Eco, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
        OnboardingPage(Icons.Filled.CloudSync, R.string.onboarding_sync_title, R.string.onboarding_sync_body),
        OnboardingPage(Icons.Filled.TaskAlt, R.string.onboarding_tasks_title, R.string.onboarding_tasks_body),
        OnboardingPage(Icons.Filled.Event, R.string.onboarding_calendar_title, R.string.onboarding_calendar_body),
        OnboardingPage(Icons.AutoMirrored.Filled.Notes, R.string.onboarding_notes_title, R.string.onboarding_notes_body),
        OnboardingPage(Icons.Outlined.CheckCircle, R.string.onboarding_ready_title, R.string.onboarding_ready_body),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.primaryContainer.copy(alpha = 0.55f),
                        scheme.background,
                        scheme.secondaryContainer.copy(alpha = 0.25f),
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isLast) {
                    TextButton(onClick = onFinished) {
                        Text(stringResource(R.string.skip))
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { page ->
                OnboardingPageContent(
                    page = pages[page],
                    showLanguagePicker = page == 0,
                    language = language,
                    onLanguageChange = onLanguageChange,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    val width by animateFloatAsState(
                        targetValue = if (selected) 22f else 8f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dotWidth",
                    )
                    val color by animateColorAsState(
                        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
                        label = "dotColor",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(8.dp)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }

            Button(
                onClick = {
                    if (isLast) {
                        onFinished()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(TaskDavRadii.chip),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (isLast) R.string.get_started else R.string.next,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    showLanguagePicker: Boolean,
    language: LanguagePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(TaskDavRadii.hero))
                .background(scheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = scheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showLanguagePicker) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_pick_language),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LanguageChipRow(language = language, onLanguageChange = onLanguageChange)
        }
    }
}

@Composable
fun LanguageChipRow(
    language: LanguagePreference,
    onLanguageChange: (LanguagePreference) -> Unit,
) {
    val options = listOf(
        LanguagePreference.SYSTEM to R.string.language_system,
        LanguagePreference.ENGLISH to R.string.language_english,
        LanguagePreference.SPANISH to R.string.language_spanish,
        LanguagePreference.ITALIAN to R.string.language_italian,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (pref, labelRes) ->
            val selected = language == pref
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TaskDavRadii.chip))
                    .clickable { onLanguageChange(pref) },
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                shape = RoundedCornerShape(TaskDavRadii.chip),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
