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
import androidx.compose.foundation.lazy.items // Added for LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
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
// import androidx.compose.runtime.LaunchedEffect // Replaced by DisposableEffect for listener management
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
import com.google.firebase.database.DataSnapshot // Added
import com.google.firebase.database.DatabaseError // Added
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener // Added
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Data class to hold user event information
data class UserEventData(
    val userId: String,
    val eventName: String?,
    val eventDate: String?
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(userId: String?, database: FirebaseDatabase) {
    var resultText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var userEventList by remember { mutableStateOf(listOf<UserEventData>()) }

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
            onDismiss = { showDialog = false },
            onAccept = { name, date ->
                resultText = "Nombre: $name\nFecha: ${date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
                showDialog = false

                if (userId != null) {
                    val formattedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val eventInfo = mapOf(
                        "name" to name,
                        "date" to formattedDate
                    )
                    database.getReference("users").child(userId).child("eventDetails")
                        .setValue(eventInfo)
                        .addOnSuccessListener {
                            Log.d("MainScreen", "Event details saved for user: $userId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainScreen", "Failed to save event for user: $userId", e)
                        }
                } else {
                    Log.w("MainScreen", "Cannot save event: userId is null")
                }
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
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth() // Ensure Column takes full width for list items
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Lista de Eventos de Usuarios:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))

            if (userEventList.isEmpty()) {
                Text("Cargando eventos o no hay eventos...", modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(userEventList) { userEvent ->
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text("Usuario: ${userEvent.userId.take(8)}...", style = MaterialTheme.typography.titleMedium)
                            userEvent.eventName?.let { Text("Evento: $it", style = MaterialTheme.typography.bodyMedium) }
                            userEvent.eventDate?.let { Text("Fecha: $it", style = MaterialTheme.typography.bodyMedium) }
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
    onDismiss: () -> Unit,
    onAccept: (name: String, selectedDate: LocalDate) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información Personal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ingresa tu nombre") },
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CalendarComponent(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(selectedDate.withDayOfMonth(1)) }
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
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
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
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        }, CircleShape
                    ), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = day.toString(),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
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
