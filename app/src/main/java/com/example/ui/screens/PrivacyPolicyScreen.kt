package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.SafeVoiceCard
import com.example.ui.components.SafeVoiceScaffold
import com.example.ui.theme.SafeBlueContainer
import com.example.ui.theme.SafeBlueOnContainer
import com.example.ui.theme.SafeBluePrimary

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    SafeVoiceScaffold(
        title = "Privacy Policy",
        onBackClick = onBack,
        backTestTag = "privacy_policy_back_button"
    ) { modifier ->
        LazyColumn(
            modifier = modifier
                .padding(horizontal = 20.dp)
                .testTag("privacy_policy_list"),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "policy_header", contentType = "header") {
                SafeVoiceCard(
                    containerColor = SafeBlueContainer.copy(alpha = 0.35f),
                    modifier = Modifier.testTag("privacy_policy_header_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SafeBlueContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Policy,
                                        contentDescription = "Privacy Policy for Safe Voice",
                                        tint = SafeBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Privacy Policy for Safe Voice",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = SafeBlueContainer
                                ) {
                                    Text(
                                        text = "Effective Date: November 24, 2026",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = SafeBlueOnContainer,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Welcome to Safe Voice. Your privacy and security matter to us. This Privacy Policy explains how we collect, use, and protect your information when you use the Safe Voice application (\"App\").",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "section_1", contentType = "section") {
                PolicySectionCard(
                    title = "1. Information We Collect",
                    intro = "Depending on how you use the App, we may collect limited information necessary for functionality:",
                    bullets = listOf(
                        "Account Information: When you sign in with Google, we receive basic profile details such as your name, email address, and profile picture, strictly as permitted by you during sign-in.",
                        "Voice & Audio Input: If the App includes voice features, audio is processed solely to provide the requested functionality within the App and is never sold or shared for advertising.",
                        "Technical & Usage Data: Basic device or diagnostic information (such as app version, crash logs, or general performance data) may be processed to keep the App stable and reliable."
                    )
                )
            }

            item(key = "section_2", contentType = "section") {
                PolicySectionCard(
                    title = "2. How We Use Information",
                    intro = "We use collected information only to:",
                    bullets = listOf(
                        "Provide, operate, and improve Safe Voice features.",
                        "Authenticate users securely via Google Sign-In.",
                        "Respond to user support requests and resolve technical issues.",
                        "Comply with applicable legal obligations."
                    )
                )
            }

            item(key = "section_3", contentType = "section") {
                PolicySectionCard(
                    title = "3. Data Sharing and Disclosure",
                    intro = "We do not sell, rent, or trade your personal information to third parties. Information may only be shared:",
                    bullets = listOf(
                        "With trusted service providers or infrastructure needed to run the App, under strict confidentiality obligations.",
                        "When required by law or to protect user safety and App integrity."
                    )
                )
            }

            item(key = "section_4", contentType = "section") {
                PolicySectionCard(
                    title = "4. Data Security and Retention",
                    intro = "We take reasonable technical and organizational measures to protect your data against unauthorized access, alteration, or loss. Data is retained only for as long as needed to provide the App's services or meet legal requirements."
                )
            }

            item(key = "section_5", contentType = "section") {
                PolicySectionCard(
                    title = "5. Your Rights and Choices",
                    intro = "You can review, update, or request deletion of your account data, and revoke App permissions anytime through your Google Account permissions or device settings."
                )
            }

            item(key = "section_6", contentType = "section") {
                PolicySectionCard(
                    title = "6. Children's Privacy",
                    intro = "Safe Voice is not intended for children under the age of 13 without appropriate parental consent, and we do not knowingly collect personal information from children without authorization."
                )
            }

            item(key = "section_7", contentType = "section") {
                PolicySectionCard(
                    title = "7. Changes to This Privacy Policy",
                    intro = "We may update this Privacy Policy from time to time. Any updates will be posted on this page with an updated effective date."
                )
            }

            item(key = "section_8", contentType = "contact") {
                SafeVoiceCard(
                    modifier = Modifier.testTag("privacy_policy_contact_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "8. Contact Us",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SafeBluePrimary
                        )
                        Text(
                            text = "If you have any questions about this Privacy Policy or Safe Voice, please reach out through our official support listing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "App Name: Safe Voice",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySectionCard(
    title: String,
    intro: String,
    bullets: List<String> = emptyList()
) {
    SafeVoiceCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SafeBluePrimary
            )
            Text(
                text = intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bullets.forEach { bullet ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafeBluePrimary
                            )
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
