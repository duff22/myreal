package com.example.myrealtv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.data.local.UserProfile
import com.example.myrealtv.ui.theme.tvFocusHighlight
import kotlinx.coroutines.launch

private val profileColors = listOf(
    Color(0xFF3B82F6), // Blue
    Color(0xFF10B981), // Green
    Color(0xFFF59E0B), // Yellow
    Color(0xFFEF4444), // Red
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899)  // Pink
)

private fun getProfileColor(name: String): Color {
    val index = Math.abs(name.hashCode()) % profileColors.size
    return profileColors[index]
}

@Composable
fun ProfileScreen(
    onProfileSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var profiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    
    val householdId = remember { ServiceLocator.getHouseholdId() ?: "default_household" }
    val scope = rememberCoroutineScope()

    // Query profiles from database on load
    LaunchedEffect(householdId) {
        profiles = ServiceLocator.database.userProfileDao().getProfilesForHousehold(householdId)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Text(
                text = if (profiles.isEmpty()) "Create your first profile" else "Who's watching?",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Render dynamically generated user profiles
                profiles.forEach { profile ->
                    ProfileCard(
                        name = profile.profileName,
                        color = getProfileColor(profile.profileName),
                        onClick = {
                            ServiceLocator.saveActiveProfile(profile.profileName)
                            onProfileSelected()
                        }
                    )
                }

                // Add Profile Card
                AddProfileCard(
                    onClick = { showAddDialog = true }
                )
            }
        }
    }

    // Dynamic Profile Creation Dialog (TV-optimized)
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                val saveInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                val newProfile = UserProfile(householdId, name)
                                ServiceLocator.database.userProfileDao().insertProfile(newProfile)
                                profiles = ServiceLocator.database.userProfileDao().getProfilesForHousehold(householdId)
                                showAddDialog = false
                                newProfileName = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D2FF),
                        contentColor = Color.Black
                    ),
                    interactionSource = saveInteraction,
                    modifier = Modifier.tvFocusHighlight(saveInteraction, RoundedCornerShape(8.dp))
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                val cancelInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        showAddDialog = false
                        newProfileName = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    interactionSource = cancelInteraction,
                    modifier = Modifier.tvFocusHighlight(cancelInteraction, RoundedCornerShape(8.dp))
                ) {
                    Text("Cancel")
                }
            },
            title = {
                Text("Create New Profile", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val inputInteraction = remember { MutableInteractionSource() }
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Profile Name", color = Color.White.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00D2FF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        interactionSource = inputInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusHighlight(inputInteraction, RoundedCornerShape(8.dp))
                    )
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ProfileCard(
    name: String,
    color: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .width(130.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .tvFocusHighlight(interactionSource, shape = RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Monogram Avatar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = name,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AddProfileCard(
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .width(130.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .tvFocusHighlight(interactionSource, shape = RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Plus Icon Avatar
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 44.sp,
                fontWeight = FontWeight.Light
            )
        }

        Text(
            text = "Add Profile",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
