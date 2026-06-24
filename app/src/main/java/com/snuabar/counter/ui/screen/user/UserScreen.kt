package com.snuabar.counter.ui.screen.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.snuabar.counter.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    viewModel: UserViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState(initial = emptyList())
    val currentUserId by viewModel.currentUserId.collectAsState(initial = null)
    val showDialog by viewModel.showAddUserDialog.collectAsState()
    val newUserName by viewModel.newUserName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户管理") },
                actions = {
                    IconButton(onClick = { viewModel.setShowAddUserDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "添加用户")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (users.isEmpty()) {
                EmptyUserState()
            } else {
                UserList(
                    users = users,
                    currentUserId = currentUserId,
                    onSwitchUser = { viewModel.switchUser(it) },
                    onDeleteUser = { viewModel.deleteUser(it) }
                )
            }
        }

        if (showDialog) {
            AddUserDialog(
                userName = newUserName,
                onUserNameChange = { viewModel.setNewUserName(it) },
                onConfirm = { viewModel.createUser(newUserName) },
                onDismiss = { viewModel.setShowAddUserDialog(false) }
            )
        }
    }
}

@Composable
private fun EmptyUserState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无用户",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "点击右上角添加用户",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun UserList(
    users: List<User>,
    currentUserId: Long?,
    onSwitchUser: (Long) -> Unit,
    onDeleteUser: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(users) { user ->
            UserItem(
                user = user,
                isCurrentUser = user.id == currentUserId,
                onSwitch = { onSwitchUser(user.id) },
                onDelete = { onDeleteUser(user.id) }
            )
        }
    }
}

@Composable
private fun UserItem(
    user: User,
    isCurrentUser: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (isCurrentUser) {
                    Text(
                        text = "当前用户",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row {
                if (!isCurrentUser) {
                    OutlinedButton(onClick = onSwitch) {
                        Text("切换")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddUserDialog(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加用户") },
        text = {
            OutlinedTextField(
                value = userName,
                onValueChange = onUserNameChange,
                label = { Text("用户名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = userName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
