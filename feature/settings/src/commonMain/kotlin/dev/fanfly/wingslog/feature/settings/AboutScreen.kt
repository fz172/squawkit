package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.appinfo.getAppVersion
import dev.fanfly.wingslog.core.ui.adaptive.compose.ConstrainedTopBar
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.feature.settings.generated.resources.about_contact_support
import wingslog.feature.settings.generated.resources.about_contact_support_subtitle
import wingslog.feature.settings.generated.resources.about_copyright
import wingslog.feature.settings.generated.resources.about_rate
import wingslog.feature.settings.generated.resources.about_section_help
import wingslog.feature.settings.generated.resources.about_section_legal
import wingslog.feature.settings.generated.resources.about_terms
import wingslog.feature.settings.generated.resources.app_version
import wingslog.feature.settings.generated.resources.settings_about
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes

private val AppTileSize = 96.dp
private val AppTileIconSize = 48.dp

/**
 * About SquawkIt: the version, the legal page, and the ways to reach us. Every row is a link out,
 * so the rows exist only where the host has somewhere to send them — web has no store to rate in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  navController: NavController,
  appCapability: AppCapability = koinInject(),
) {
  val uriHandler = LocalUriHandler.current

  Scaffold(
    topBar = {
      ConstrainedTopBar {
        WingsLogTopAppBar(
          title = stringResource(SettingsRes.string.settings_about),
          onBackClick = { navController.popBackStack() },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .padding(innerPadding)
        .fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .constrainedContentWidth(ContentWidth.Reading)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
      ) {
        AppIdentity()

        val termsUrl = appCapability.termsUrl
        if (termsUrl != null) {
          GroupedSection(stringResource(SettingsRes.string.about_section_legal)) {
            SettingsRowGroup(
              listOf {
                SettingsRow(
                  icon = Icons.Default.Gavel,
                  title = stringResource(SettingsRes.string.about_terms),
                  onClick = { uriHandler.openUri(termsUrl) },
                )
              }
            )
          }
        }

        val supportUrl = appCapability.supportUrl
        val storeListingUrl = appCapability.storeListingUrl
        if (supportUrl != null || storeListingUrl != null) {
          GroupedSection(stringResource(SettingsRes.string.about_section_help)) {
            SettingsRowGroup(
              buildList {
                if (supportUrl != null) {
                  add {
                    SettingsRow(
                      icon = Icons.Default.SupportAgent,
                      title = stringResource(SettingsRes.string.about_contact_support),
                      subtitle = stringResource(SettingsRes.string.about_contact_support_subtitle),
                      onClick = { uriHandler.openUri(supportUrl) },
                    )
                  }
                }
                if (storeListingUrl != null) {
                  add {
                    SettingsRow(
                      icon = Icons.Default.Star,
                      title = stringResource(SettingsRes.string.about_rate),
                      onClick = { uriHandler.openUri(storeListingUrl) },
                    )
                  }
                }
              }
            )
          }
        }

        Spacer(Modifier.weight(1f))
        Text(
          text = stringResource(SettingsRes.string.about_copyright),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

/** The app mark, name and version — the version in mono, since it is an identifier. */
@Composable
private fun AppIdentity() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Box(
      modifier = Modifier
        .size(AppTileSize)
        .clip(RoundedCornerShape(Spacing.extraLarge))
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Default.Flight,
        contentDescription = null,
        modifier = Modifier.size(AppTileIconSize),
        tint = MaterialTheme.colorScheme.primary,
      )
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = stringResource(CoreRes.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = stringResource(SettingsRes.string.app_version, getAppVersion()),
        style = WingslogTypography.dataMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
