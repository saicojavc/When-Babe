package com.saico.whenbabe.sceen

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete // Added
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class UserEventData(
    val userId: String,
    val eventName: String?,
    val eventDate: String?
)

@RequiresApi(Build.VERSION_CODES.O)
private fun safeParseISODate(dateString: String?): LocalDate? {
    return if (dateString != null) {
        try {
            LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: DateTimeParseException) {
            Log.e("safeParseISODate", "Error parsing date string: $dateString", e)
            null
        }
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(userId: String?, database: FirebaseDatabase) {
    var showDialog by remember { mutableStateOf(false) }
    var userEventList by remember { mutableStateOf(listOf<UserEventData>()) }
    var eventToEdit by remember { mutableStateOf<UserEventData?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserEventData?>(null) }

    DisposableEffect(key1 = database) {
        val usersRef = database.getReference("users")
        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<UserEventData>()
                snapshot.children.forEach { userSnapshot ->
                    val currentLoopUserId = userSnapshot.key ?: ""
                    val eventDetailsSnapshot = userSnapshot.child("eventDetails")
                    val eventName = eventDetailsSnapshot.child("name").getValue(String::class.java)
                    val eventDate = eventDetailsSnapshot.child("date").getValue(String::class.java)
                    if (currentLoopUserId.isNotEmpty()) {
                        tempList.add(UserEventData(currentLoopUserId, eventName, eventDate))
                    }
                }
                userEventList = tempList
                Log.d("MainScreen", "User event list updated: ${userEventList.size} items")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("MainScreen", "Failed to read user events from Firebase", error.toException())
            }
        }
        usersRef.addValueEventListener(valueEventListener)
        onDispose {
            usersRef.removeEventListener(valueEventListener)
            Log.d("MainScreen", "Firebase listener removed on dispose.")
        }
    }

    if (showDialog) {
        CustomAlertDialog(
            initialName = eventToEdit?.eventName,
            initialDateString = eventToEdit?.eventDate,
            onDismiss = {
                showDialog = false
                eventToEdit = null
            },
            onAccept = { name, date ->
                showDialog = false
                if (userId != null) { // Save/update the event for the current user
                    val formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val eventInfo = mapOf("name" to name, "date" to formattedDate)
                    database.getReference("users").child(userId).child("eventDetails")
                        .setValue(eventInfo)
                        .addOnSuccessListener {
                            Log.d("MainScreen", "Event details saved for user: $userId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainScreen", "Failed to save event for user: $userId", e)
                        }
                } else {
                    Log.w("MainScreen", "Cannot save event: current userId is null")
                }
                eventToEdit = null
            }
        )
    }

    if (showDeleteDialog && userToDelete != null) {
        ConfirmDeleteDialog(
            userName = userToDelete?.userId?.take(8), // Show part of the ID or name if available
            onConfirm = {
                val userIdToDelete = userToDelete!!.userId
                database.getReference("users").child(userIdToDelete).removeValue()
                    .addOnSuccessListener {
                        Log.d("MainScreen", "User data deleted for user: $userIdToDelete")
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainScreen", "Failed to delete data for user: $userIdToDelete", e)
                    }
                showDeleteDialog = false
                userToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                userToDelete = null
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Cuando llegas Alana")
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                eventToEdit = null
                showDialog = true
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Event")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth()
        ) {
            Text("Tu User ID: ${userId?.take(8)}...", modifier = Modifier.padding(8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Lista de Eventos de Usuarios:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))

            if (userEventList.isEmpty()) {
                Text("Cargando eventos o no hay eventos...", modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(userEventList) { eventData ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), // Adjusted padding
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Usuario: ${eventData.userId.take(8)}...", style = MaterialTheme.typography.titleMedium)
                                eventData.eventName?.let { Text("Evento: $it", style = MaterialTheme.typography.bodyMedium) }
                                eventData.eventDate?.let { dateStr ->
                                    val parsedDate = safeParseISODate(dateStr)
                                    if (parsedDate != null) {
                                        Text("Fecha: ${parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text("Fecha: $dateStr (formato inválido)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (eventData.userId == userId) {
                                Row {
                                    IconButton(onClick = {
                                        eventToEdit = eventData
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar Evento")
                                    }
                                    IconButton(onClick = {
                                        userToDelete = eventData
                                        showDeleteDialog = true
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar Usuario", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CustomAlertDialog(
    initialName: String? = null,
    initialDateString: String? = null,
    onDismiss: () -> Unit,
    onAccept: (name: String, selectedDate: LocalDate) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName ?: "") }
    var selectedDate by remember(initialDateString) {
        mutableStateOf(safeParseISODate(initialDateString) ?: LocalDate.now())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName != null) "Editar Evento" else "Información Personal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Evento") },
                    placeholder = { Text("Ingresa el nombre del evento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "Fecha seleccionada: ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CalendarComponent(selectedDate = selectedDate, onDateSelected = { selectedDate = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) { onAccept(name, selectedDate) } },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        modifier = Modifier.widthIn(min = 320.dp, max = 400.dp)
    )
}

@Composable
fun ConfirmDeleteDialog(
    userName: String?, // Or any other identifier for the user
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar Eliminación") },
        text = { Text("¿Estás seguro de que quieres eliminar todos los datos del usuario ${userName ?: "seleccionado"}? Esta acción no se puede deshacer.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarComponent(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember(selectedDate.year, selectedDate.monthValue) { mutableStateOf(selectedDate.withDayOfMonth(1)) }
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Mes anterior", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Mes siguiente", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("D", "L", "M", "M", "J", "V", "S").forEach { day ->
                Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val firstDayOfMonth = currentMonth.withDayOfMonth(1)
        val lastDayOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday as 0
        val totalDays = lastDayOfMonth.dayOfMonth
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(200.dp)) {
            items(firstDayOfWeek) { Spacer(modifier = Modifier.size(40.dp)) }
            items(totalDays) { dayIndex ->
                val day = dayIndex + 1
                val date = currentMonth.withDayOfMonth(day)
                val isSelected = date == selectedDate
                val isToday = date == LocalDate.now()
                Box(
                    modifier = Modifier.size(40.dp).clickable { onDateSelected(date) }.background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else -> Color.Transparent
                        }, CircleShape
                    ), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.toString(),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday && !isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

