package com.f7.downloadmanager.screens

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7PrimaryButton
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.F7TopBar
import com.f7.downloadmanager.AdsViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun RemoveAdsScreen(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val vm: AdsViewModel = viewModel()
    val billing by vm.billingState.collectAsStateWithLifecycle()
    val adFree by vm.adFree.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    LaunchedEffect(billing.message) {
        billing.message?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .navigationBarsPadding()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.ads_remove_title),
                        subtitle = stringResource(R.string.ads_remove_subtitle),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }

            item {
                if (adFree) {
                    ActiveCard()
                } else {
                    PurchaseCard(
                        priceText = billing.priceText,
                        busy = billing.busy,
                        billingAvailable = billing.billingAvailable,
                        onBuy = { vm.purchase(activity) },
                        onRestore = { vm.restore() }
                    )
                }
            }

            if (!billing.purchased) {
                item {
                    RewardCard(
                        onWatch = {
                            vm.watchRewarded(activity) { granted ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(
                                            if (granted) R.string.ads_rewarded_granted
                                            else R.string.ads_rewarded_unavailable,
                                            vm.rewardDurationLabel
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.ads_purchase_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F2E5C), Color(0xFF1F5AE0), Color(0xFF2ED3B7))
                )
            )
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_f7_verified),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.ads_remove_active_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ads_remove_active_message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f)
            )
        }
    }
}

@Composable
private fun PurchaseCard(
    priceText: String,
    busy: Boolean,
    billingAvailable: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
) {
    F7Card {
        F7SectionHeading(
            title = stringResource(R.string.ads_remove_title),
            subtitle = stringResource(R.string.ads_remove_subtitle)
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = priceText,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ads_remove_subtitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_f7_workspace_premium),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        F7PrimaryButton(
            text = stringResource(
                if (busy) R.string.ads_remove_buying else R.string.ads_remove_buy
            ),
            icon = painterResource(R.drawable.ic_f7_shopping_cart),
            onClick = onBuy
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.ads_restore))
            }
        }
        if (!billingAvailable) {
            Text(
                text = stringResource(R.string.ads_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RewardCard(onWatch: () -> Unit) {
    F7Card {
        F7SectionHeading(
            title = stringResource(R.string.ads_rewarded_title, "2 hours"),
            subtitle = stringResource(R.string.ads_rewarded_message)
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onWatch) {
            Icon(
                painterResource(R.drawable.ic_f7_play_arrow),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ads_rewarded_watch))
        }
    }
}
