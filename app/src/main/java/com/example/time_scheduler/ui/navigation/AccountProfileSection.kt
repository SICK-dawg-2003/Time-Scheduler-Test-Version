package com.example.time_scheduler.ui.navigation

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class SyncAccount(
    val name: String,
    val email: String,
    val provider: String = "Google",
    val badgeColorHex: Long = 0xFF5B4B9E
)

object AccountPrefsManager {
    private const val PREFS_NAME = "unisync_account_prefs"
    private const val KEY_ACTIVE_EMAIL = "active_email"
    private const val KEY_SAVED_ACCOUNTS = "saved_accounts"
    private const val KEY_LAST_SYNC = "last_sync_time"

    private val avatarPalette = listOf(
        0xFF2E7D32, // Green
        0xFF1565C0, // Blue
        0xFF6A1B9A, // Purple
        0xFFC62828, // Red
        0xFFEF6C00  // Orange
    )

    fun loadAccounts(context: Context): List<SyncAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val merged = linkedMapOf<String, SyncAccount>()

        // 1. Query Android AccountManager for on-device Google accounts
        try {
            val deviceGoogleAccounts = AccountManager.get(context).getAccountsByType("com.google")
            deviceGoogleAccounts.forEachIndexed { idx, acct ->
                val displayName = acct.name.substringBefore("@")
                    .replace('.', ' ')
                    .replaceFirstChar { it.uppercase() }
                merged[acct.name] = SyncAccount(
                    name = displayName,
                    email = acct.name,
                    provider = "Google",
                    badgeColorHex = avatarPalette[idx % avatarPalette.size]
                )
            }
        } catch (_: SecurityException) {
            // Fallback to locally stored accounts if runtime account visibility is restricted
        }

        // 2. Load locally persisted accounts
        val rawSet = prefs.getStringSet(KEY_SAVED_ACCOUNTS, emptySet()) ?: emptySet()
        rawSet.forEachIndexed { idx, entry ->
            val parts = entry.split("|")
            if (parts.size >= 2) {
                val name = parts[0]
                val email = parts[1]
                val colorHex = parts.getOrNull(2)?.toLongOrNull()
                    ?: avatarPalette[(merged.size + idx) % avatarPalette.size]
                merged[email] = SyncAccount(
                    name = name,
                    email = email,
                    provider = "Google",
                    badgeColorHex = colorHex
                )
            }
        }

        // 3. Seed a default local profile if no accounts exist yet
        if (merged.isEmpty()) {
            val defaultAccount = SyncAccount(
                name = "UniSync Student",
                email = "student.sync@gmail.com",
                provider = "Google",
                badgeColorHex = avatarPalette[0]
            )
            merged[defaultAccount.email] = defaultAccount
            saveAccounts(context, merged.values.toList())
        }

        return merged.values.toList()
    }

    fun saveAccounts(context: Context, accounts: List<SyncAccount>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = accounts.map { "${it.name}|${it.email}|${it.badgeColorHex}" }.toSet()
        prefs.edit { putStringSet(KEY_SAVED_ACCOUNTS, serialized) }
    }

    fun getActiveAccount(context: Context, accounts: List<SyncAccount>): SyncAccount {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null)
        return accounts.find { it.email == activeEmail } ?: accounts.first()
    }

    fun setActiveAccount(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_ACTIVE_EMAIL, email)
            putString(KEY_LAST_SYNC, LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")))
        }
    }

    fun getLastSyncTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SYNC, "Synced") ?: "Synced"
    }

    fun addLocalAccount(context: Context, name: String, email: String): List<SyncAccount> {
        val current = loadAccounts(context).toMutableList()
        val colorHex = avatarPalette[current.size % avatarPalette.size]
        val newAccount = SyncAccount(
            name = name.trim().ifBlank { email.substringBefore("@") },
            email = email.trim(),
            provider = "Google",
            badgeColorHex = colorHex
        )
        current.removeAll { it.email.equals(newAccount.email, ignoreCase = true) }
        current.add(newAccount)
        saveAccounts(context, current)
        setActiveAccount(context, newAccount.email)
        return current
    }
}

/**
 * Compact Profile Pill anchored at the bottom of the Navigation Drawer (matches 3rd screenshot).
 */
@Composable
fun DrawerProfileFooter(
    activeAccount: SyncAccount,
    lastSyncLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Circular Profile Avatar with active ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(activeAccount.badgeColorHex))
            ) {
                Text(
                    text = activeAccount.name.firstOrNull()?.uppercase() ?: "U",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Email & Provider ("Google • Synced")
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeAccount.email,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${activeAccount.provider} • $lastSyncLabel",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Switch Google Account",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Full Google-style Account Switcher & Sync Modal (matches 2nd screenshot).
 */
@Composable
fun AccountSwitcherDialog(
    accounts: List<SyncAccount>,
    activeAccount: SyncAccount,
    onDismiss: () -> Unit,
    onSelectAccount: (SyncAccount) -> Unit,
    onAddAccount: (name: String, email: String) -> Unit,
    onSyncNow: () -> Unit
) {
    val context = LocalContext.current
    var isAccountListExpanded by remember { mutableStateOf(true) }
    var isAddAccountDialogOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val otherAccounts = remember(accounts, activeAccount) {
        accounts.filter { it.email != activeAccount.email }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Top Close (X) Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Account Switcher",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "UniSync Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(32.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Rounded Accounts Card Container
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Active Account Header Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAccountListExpanded = !isAccountListExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(activeAccount.badgeColorHex))
                                ) {
                                    Text(
                                        text = activeAccount.name.firstOrNull()?.uppercase() ?: "U",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = CircleShape,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeAccount.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeAccount.email,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isAccountListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Toggle Accounts List",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Expandable Other Accounts & Actions
                        AnimatedVisibility(visible = isAccountListExpanded) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )

                                otherAccounts.forEach { account ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectAccount(account)
                                                Toast.makeText(
                                                    context,
                                                    "Switched & synced to ${account.email}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onDismiss()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(account.badgeColorHex))
                                        ) {
                                            Text(
                                                text = account.name.firstOrNull()?.uppercase() ?: "G",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = account.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = account.email,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )

                                // "+ Add another account"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isAddAccountDialogOpen = true }
                                        .padding(horizontal = 20.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add another account",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Add another account",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // "Manage accounts on this device" -> Opens Android Account & Sync Settings
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to open device account settings",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ManageAccounts,
                                        contentDescription = "Manage accounts on this device",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Manage accounts on this device",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pill Action Button 1: Sync Schedule Now
                OutlinedButton(
                    onClick = {
                        onSyncNow()
                        Toast.makeText(
                            context,
                            "Schedule synced with ${activeAccount.email}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Schedule",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync Schedule with Google Account",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pill Action Button 2: Add Device Google Account via Android Settings
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            isAddAccountDialogOpen = true
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Manage your Google Account",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manage your Google Account",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer: Privacy Policy • Terms of Service
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "  •  ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Terms of Service",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (isAddAccountDialogOpen) {
        AddAccountPromptDialog(
            onDismiss = { isAddAccountDialogOpen = false },
            onConfirm = { name, email ->
                onAddAccount(name, email)
                isAddAccountDialogOpen = false
                Toast.makeText(context, "Added & switched to $email", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun AddAccountPromptDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val isEmailValid = email.contains("@") && email.contains(".")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Sync Account",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Google Email (e.g. name@gmail.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isEmailValid,
                onClick = { onConfirm(displayName, email) }
            ) {
                Text("Add & Sync", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}