package com.f7.downloadmanager.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7LogoMark
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.F7TopBar
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.BuildConfig
import com.f7.downloadmanager.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .navigationBarsPadding()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.about_title),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    F7LogoMark(size = 82.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "F7 Download Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    com.f7.core.ui.components.F7Pill(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME)
                    )
                }
            }
            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.about_features_title))
                    Spacer(Modifier.height(10.dp))
                    FeatureRow(stringResource(R.string.about_feature_multithread))
                    FeatureRow(stringResource(R.string.about_feature_service))
                    FeatureRow(stringResource(R.string.about_feature_hls))
                    FeatureRow(stringResource(R.string.about_feature_security))
                    FeatureRow(stringResource(R.string.about_feature_resume))
                }
            }
            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.about_not_included_title))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_not_included_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.about_benchmark_title))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_benchmark_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.about_license))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_license_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_source_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable {
                                openUrl(context, "https://www.gnu.org/licenses/gpl-3.0.txt")
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_code),
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.about_view_license),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_libraries),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_inspiration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                F7Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_f7_shield),
                            null,
                            tint = LocalF7Colors.current.success,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.about_privacy_short),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.about_privacy_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.about_thanks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(LocalF7Colors.current.successContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_f7_check),
                null,
                tint = LocalF7Colors.current.success,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .navigationBarsPadding()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.privacy_title),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.privacy_updated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                F7Card {
                    Text(
                        text = stringResource(R.string.privacy_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                PrivacySection(
                    title = stringResource(R.string.privacy_section_data),
                    body = stringResource(R.string.privacy_data_desc)
                )
            }
            item {
                PrivacySection(
                    title = stringResource(R.string.privacy_section_network),
                    body = stringResource(R.string.privacy_network_desc)
                )
            }
            item {
                PrivacySection(
                    title = stringResource(R.string.privacy_section_ads),
                    body = stringResource(R.string.privacy_ads_desc)
                )
            }
            item {
                PrivacySection(
                    title = stringResource(R.string.privacy_section_storage),
                    body = stringResource(R.string.privacy_storage_desc)
                )
            }
            item {
                PrivacySection(
                    title = stringResource(R.string.privacy_section_contact),
                    body = stringResource(R.string.privacy_contact_desc)
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_shield),
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.about_privacy_short),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    F7Card {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

