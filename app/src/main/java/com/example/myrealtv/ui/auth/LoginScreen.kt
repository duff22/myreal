package com.example.myrealtv.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myrealtv.data.ServiceLocator
import com.example.myrealtv.ui.theme.tvFocusHighlight
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF020617)  // Slate 955
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Branding Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "MyRealTV",
                    color = Color(0xFF00D2FF),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dedicated VOD Client",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sync your playback history and watched states directly with your VPS dashboard. Fully D-pad remote-first navigation.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
            
            // Login Form Column
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Xtream Codes Login",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                
                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Server URL Field
                val serverInteractionSource = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    interactionSource = serverInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusHighlight(serverInteractionSource, RoundedCornerShape(8.dp))
                )
                
                // Username Field
                val usernameInteractionSource = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    interactionSource = usernameInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusHighlight(usernameInteractionSource, RoundedCornerShape(8.dp))
                )
                
                // Password Field
                val passwordInteractionSource = remember { MutableInteractionSource() }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00D2FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    ServiceLocator.xtreamBaseUrl = serverUrl
                                    val response = ServiceLocator.xtreamApi.login(username, password)
                                    if (response.user_info != null) {
                                        ServiceLocator.saveLogin(serverUrl, username, password)
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = "Invalid credentials or inactive account."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Connection failed: ${e.localizedMessage ?: "Unknown error"}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    ),
                    interactionSource = passwordInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusHighlight(passwordInteractionSource, RoundedCornerShape(8.dp))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Connect Button
                val buttonInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                ServiceLocator.xtreamBaseUrl = serverUrl
                                val response = ServiceLocator.xtreamApi.login(username, password)
                                if (response.user_info != null) {
                                    ServiceLocator.saveLogin(serverUrl, username, password)
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Invalid credentials or inactive account."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Connection failed: ${e.localizedMessage ?: "Unknown error"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D2FF),
                        contentColor = Color.Black
                    ),
                    interactionSource = buttonInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .tvFocusHighlight(buttonInteractionSource, RoundedCornerShape(8.dp)),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
