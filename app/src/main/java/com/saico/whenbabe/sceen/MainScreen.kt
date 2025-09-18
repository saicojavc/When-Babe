package com.saico.whenbabe.sceen

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lint.kotlin.metadata.Visibility
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields
import java.util.Locale

// Admin User ID
private const val ADMIN_USER_ID = "0be2f871-aa42-4258-81b4-383dd7bf1860"

data class UserEventData(
    val userId: String,
    val eventId: String, // Unique ID for each event
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
    var eventToDelete by remember { mutableStateOf<UserEventData?>(null) }
    var showAllEventsCalendarDialog by remember { mutableStateOf(false) } // State for the new calendar dialog

    DisposableEffect(key1 = database) {
        val usersRef = database.getReference("users")
        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tempList = mutableListOf<UserEventData>()
                snapshot.children.forEach { userSnapshot ->
                    val currentLoopUserId = userSnapshot.key ?: ""
                    if (currentLoopUserId.isNotEmpty()) {
                        val eventsDetailsNode = userSnapshot.child("eventDetails")
                        eventsDetailsNode.children.forEach { eventSnapshot ->
                            val eventId = eventSnapshot.key ?: ""
                            val eventName = eventSnapshot.child("name").getValue(String::class.java)
                            val eventDate = eventSnapshot.child("date").getValue(String::class.java)
                            if (eventId.isNotEmpty()) {
                                tempList.add(UserEventData(currentLoopUserId, eventId, eventName, eventDate))
                            }
                        }
                    }
                }
                userEventList = tempList.sortedByDescending { safeParseISODate(it.eventDate) ?: LocalDate.MIN }
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
            initialEventId = eventToEdit?.eventId,
            initialName = eventToEdit?.eventName,
            initialDateString = eventToEdit?.eventDate,
            onDismiss = {
                showDialog = false
                eventToEdit = null
            },
            onAccept = { name, date ->
                showDialog = false
                if (userId != null) {
                    val eventInfo = mapOf("name" to name, "date" to date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    val eventDetailsRef = database.getReference("users").child(userId).child("eventDetails")

                    if (eventToEdit == null || eventToEdit?.eventId.isNullOrEmpty()) { // Create new event
                        eventDetailsRef.push().setValue(eventInfo)
                            .addOnSuccessListener {
                                Log.d("MainScreen", "New event saved for user: $userId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainScreen", "Failed to save new event for user: $userId", e)
                            }
                    } else { // Update existing event
                        val eventIdToUpdate = eventToEdit!!.eventId
                        eventDetailsRef.child(eventIdToUpdate).setValue(eventInfo)
                            .addOnSuccessListener {
                                Log.d("MainScreen", "Event updated for user: $userId, eventId: $eventIdToUpdate")
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainScreen", "Failed to update event for user: $userId, eventId: $eventIdToUpdate", e)
                            }
                    }
                }
                eventToEdit = null
            }
        )
    }

    if (showDeleteDialog && eventToDelete != null) {
        ConfirmDeleteDialog(
            eventName = eventToDelete?.eventName,
            onConfirm = {
                val eventDataToDelete = eventToDelete
                if (eventDataToDelete != null) {
                    val userIdForDelete = eventDataToDelete.userId
                    val eventIdForDelete = eventDataToDelete.eventId

                    database.getReference("users").child(userIdForDelete)
                        .child("eventDetails").child(eventIdForDelete)
                        .removeValue()
                        .addOnSuccessListener {
                            Log.d("MainScreen", "Event deleted: userId=${userIdForDelete}, eventId=${eventIdForDelete}")
                            showDeleteDialog = false
                            eventToDelete = null
                        }
                        .addOnFailureListener { e ->
                            Log.e("MainScreen", "Failed to delete event: userId=${userIdForDelete}, eventId=${eventIdForDelete}", e)
                            showDeleteDialog = false
                            eventToDelete = null
                        }
                } else {
                    showDeleteDialog = false
                    eventToDelete = null
                }
            },
            onDismiss = {
                showDeleteDialog = false
                eventToDelete = null
            }
        )
    }

    if (showAllEventsCalendarDialog) {
        AllEventsCalendarDialog(
            allEvents = userEventList,
            onDismiss = { showAllEventsCalendarDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // To push the icon to the right
            ) {
                Text(
                    text = "Cuando llegas Alana",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f) // Text takes available space
                )
                IconButton(onClick = { showAllEventsCalendarDialog = true }) {
                    Icon(imageVector = Icons.Filled.DateRange, contentDescription = "Ver calendario de todos los eventos")
                }
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
            Spacer(modifier = Modifier.height(8.dp))
            Text("Lista de Apostadores:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))

            if (userEventList.isEmpty()) {
                Text("Cargando eventos o no hay eventos...", modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(userEventList, key = { it.userId + it.eventId }) { eventData ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                eventData.eventName?.let { Text("Nombre: $it", style = MaterialTheme.typography.titleSmall) }
                                Text("Id: ${eventData.userId.take(8)}...", style = MaterialTheme.typography.bodyMedium)
                                 Text("Evento: Apuesta", style = MaterialTheme.typography.bodyMedium)
                                eventData.eventDate?.let { dateStr ->
                                    val parsedDate = safeParseISODate(dateStr)
                                    if (parsedDate != null) {
                                        Text("Fecha: ${parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text("Fecha: $dateStr (formato inválido)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Row {
                                if (eventData.userId == userId) {
                                    IconButton(onClick = {
                                        eventToEdit = eventData
                                        showDialog = true
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Editar este Evento")
                                    }
                                }
                                if (userId == ADMIN_USER_ID) {
                                    IconButton(onClick = {
                                        eventToDelete = eventData
                                        showDeleteDialog = true
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar este Evento", tint = MaterialTheme.colorScheme.error)
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
fun AllEventsCalendarDialog(
    allEvents: List<UserEventData>,
    onDismiss: () -> Unit
) {
    var currentDisplayMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()

    // Group events by date for quick lookup
    val eventsByDate = remember(allEvents) {
        allEvents.groupBy { safeParseISODate(it.eventDate) }
            .filterKeys { it != null } // Remove events with unparseable dates
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Calendario de Eventos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Month Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentDisplayMonth = currentDisplayMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ArrowBack, "Mes anterior")
                    }
                    Text(
                        text = currentDisplayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { currentDisplayMonth = currentDisplayMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ArrowForward, "Mes siguiente")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Days of the Week Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    val daysOfWeek = listOf("D", "L", "M", "X", "J", "V", "S") // Spanish initials
                    daysOfWeek.forEach {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Calendar Grid
                val firstDayOfMonth = currentDisplayMonth.atDay(1)
                val firstDayOfWeek = firstDayOfMonth.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                val daysInMonth = currentDisplayMonth.lengthOfMonth()
                val weekFields = WeekFields.of(Locale.getDefault())
                val firstDayOfMonthOffset = firstDayOfMonth.dayOfWeek.get(weekFields.dayOfWeek()) -1 // Adjust to make Sunday 0 or Monday 0 as per your week start

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Empty cells for the start of the month offset
                    items(firstDayOfMonthOffset) {
                        Box(modifier = Modifier.aspectRatio(1f)) // Maintain square cells
                    }

                    items(daysInMonth) { dayIndex ->
                        val dayNumber = dayIndex + 1
                        val date = currentDisplayMonth.atDay(dayNumber)
                        val isCurrentDay = date == today
                        val eventsOnThisDay = eventsByDate[date]

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f) // Maintain square cells
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrentDay -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        !eventsOnThisDay.isNullOrEmpty() -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    if (isCurrentDay) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrentDay || !eventsOnThisDay.isNullOrEmpty()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentDay) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (!eventsOnThisDay.isNullOrEmpty()) {
                                    Text(
                                        text = "${eventsOnThisDay.size}", // Show event count
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary, // Or another distinct color
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CustomAlertDialog(
    initialEventId: String? = null,
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
        title = { Text(if (initialEventId != null) "Editar Evento" else "Información Personal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
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
    eventName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar Eliminación") },
        text = {
            val eventDescription = if (!eventName.isNullOrBlank()) "'${eventName}'" else "este evento"
            Text("¿Estás seguro de que quieres eliminar ${eventDescription}? Esta acción no se puede deshacer.")
        },
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
            listOf("D", "L", "M", "X", "J", "V", "S").forEach { day -> // Changed M to X for Wednesday
                Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val firstDayOfMonth = currentMonth.withDayOfMonth(1)
        //val lastDayOfMonth = currentMonth.withDayOfMonth(currentMonth.lengthOfMonth())
        //val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday as 0
        val weekFields = WeekFields.of(Locale.getDefault())
        val firstDayOfMonthOffset = firstDayOfMonth.dayOfWeek.get(weekFields.dayOfWeek()) -1

        val totalDays = currentMonth.lengthOfMonth()
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(200.dp)) {
            items(firstDayOfMonthOffset) { Spacer(modifier = Modifier.size(40.dp).aspectRatio(1f)) }
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
                    ).aspectRatio(1f),
                    contentAlignment = Alignment.Center
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
