package com.example

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ScheduledEvent
import com.example.service.AlertService
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.CosmicDarkBackground
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.RedStop
import com.example.viewmodel.EventViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EventViewModel = viewModel()) {
    val context = LocalContext.current
    val events by viewModel.allEvents.collectAsStateWithLifecycle()
    val activeAlertId by AlertService.activeAlertEventId.collectAsStateWithLifecycle()
    val activeAlertTitle by AlertService.activeAlertTitle.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }

    // State for notification permission warning
    var hasNotifyPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifyPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifyPermission) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Real-time live clock ticking inside a coroutine
    var currentClockText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale("ar"))
        val dateFormat = SimpleDateFormat("EEEE، dd MMMM yyyy", Locale("ar"))
        while (true) {
            val calendar = Calendar.getInstance()
            currentClockText = timeFormat.format(calendar.time)
            currentDateText = dateFormat.format(calendar.time)
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "المنبه النشط",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "منبه المواعيد بالفلاش والصوت",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                actions = {
                    IconButton(onClick = {
                        // Open system settings for app notifications
                        val intent = Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "إعدادات الإشعارات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("add_appointment_fab"),
                shape = FloatingActionButtonDefaults.largeShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "إضافة موعد"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جدولة موعد جديد",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)
            ) {
                // Header Hero Composition Block
                item {
                    HeroHeaderCard(
                        clockText = currentClockText,
                        dateText = currentDateText,
                        totalEvents = events.size,
                        activeEvents = events.count { it.enabled },
                        hasPermission = hasNotifyPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                // Title Section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "المواعيد المجدولة",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            textAlign = TextAlign.Right
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.scale(1.1f)
                        ) {
                            Text(
                                text = "${events.size} مواعيد",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Empty state if list is empty
                if (events.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(items = events, key = { it.id }) { event ->
                        EventItemCard(
                            event = event,
                            onToggleStatus = { viewModel.toggleEventStatus(event) },
                            onDelete = { viewModel.deleteEvent(event) }
                        )
                    }
                }
            }

            // ADD APPOINTMENT DIALOG
            if (showAddDialog) {
                AddAppointmentDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { title, desc, timeMillis, voice, flash, announceTxt ->
                        viewModel.addEvent(title, desc, timeMillis, voice, flash, announceTxt)
                        showAddDialog = false
                    }
                )
            }

            // FULLSCREEN ONGOING FLASHING ALARM OVERLAY
            if (activeAlertId != null) {
                OngoingAlarmOverlay(
                    title = activeAlertTitle ?: "تنبيه موعد مجدول",
                    onDismiss = {
                        AlertService.stopActiveAlert(context)
                    }
                )
            }
        }
    }
}

@Composable
fun HeroHeaderCard(
    clockText: String,
    dateText: String,
    totalEvents: Int,
    activeEvents: Int,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Load custom generated image dynamically or display placeholder/fallback
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = "بانر منبه المواعيد",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Blur Gradient overlay for professional text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Dynamic live clock text overlaid elegantly
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "تنبيه ذكي بالفلاش والصوت",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Warning Badge if notifications are blocked
                    if (!hasPermission) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF3B30), shape = RoundedCornerShape(12.dp))
                                .clickable { onRequestPermission() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "تحذير",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تفعيل الإشعارات",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Time and statistics details
                Column {
                    Text(
                        text = clockText.ifEmpty { "00:00:00" },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp
                        ),
                        textAlign = TextAlign.Right
                    )
                    Text(
                        text = dateText.ifEmpty { "تحميل الوقت..." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        textAlign = TextAlign.Right
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = "المنبه",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "لديك الآن $activeEvents منبهات نشطة من أصل $totalEvents",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.EventNote,
                    contentDescription = "قائمة فارغة",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "لا توجد مواعيد مجدولة حالياً",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ابدأ بجدولة مقابلاتك أو أعمالك المهمة وسيقوم التطبيق بتنبيهك بوميض الفلاش ونطق اسم الموعد صوتياً في الوقت المحدد تماماً.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun EventItemCard(
    event: ScheduledEvent,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val durationString = remember(event.timeMillis) {
        val diff = event.timeMillis - System.currentTimeMillis()
        if (diff < 0) {
            "منتهي"
        } else {
            val mins = (diff / 60000).toInt()
            val hours = mins / 60
            val days = hours / 24
            when {
                days > 0 -> "خلال $days أيام"
                hours > 0 -> "خلال $hours ساعة وثوانٍ"
                mins > 0 -> "خلال $mins دقيقة"
                else -> "خلال أقل من دقيقة"
            }
        }
    }

    val eventDateTime = remember(event.timeMillis) {
        val format = SimpleDateFormat("dd MMMM - hh:mm a", Locale("ar"))
        format.format(Date(event.timeMillis))
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (event.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("event_card_${event.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (event.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (event.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (event.description.isNotEmpty()) {
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Enabled state switch
                Switch(
                    checked = event.enabled,
                    onCheckedChange = { onToggleStatus() },
                    modifier = Modifier.testTag("event_toggle_${event.id}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event time details
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "الوقت",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = eventDateTime,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (event.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else Color.LightGray.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = durationString,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = if (event.enabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Voice / Flash summary tags and delete button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (event.useVoice) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "تنبيه صوتي",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (event.useFlash) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "وميض الكاميرا",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                shape = CircleShape
                            )
                            .clickable { showDeleteConfirm = true }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "حذف الموعد",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Confirmation delete dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "حذف الموعد المجدول؟",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف موعد \"${event.title}\" نهائياً؟ سيتم إلغاء التنبيه المرتبط به.",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAppointmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, timeMillis: Long, voice: Boolean, flash: Boolean, announceTxt: String) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var customAnnouncement by remember { mutableStateOf("") }

    var useVoice by remember { mutableStateOf(true) }
    var useFlash by remember { mutableStateOf(true) }

    // Calendar state for chosen target time
    val calendar = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 5) } }
    var timeStampState by remember { mutableStateOf(calendar.timeInMillis) }

    val formattedDateTime = remember(timeStampState) {
        val format = SimpleDateFormat("yyyy / MM / dd  -  hh:mm a", Locale("ar"))
        format.format(Date(timeStampState))
    }

    // Launch Android native standard DateTime Pickers which are 100% bug free, clean, responsive and Material-supported
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                timeStampState = calendar.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                timeStampState = calendar.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        )
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .testTag("add_appointment_dialog_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Header
                Text(
                    text = "إضافة موعد وتنبيه جديد",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Input fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الموعد (مثلاً: اجتماع الشركة)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_title")
                        .padding(bottom = 12.dp),
                    singleLine = true,
                    textStyle = TextStyle(textAlign = TextAlign.Right),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("ملاحظات إضافية (اختياري)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = TextStyle(textAlign = TextAlign.Right),
                )

                // DateTime selectors
                Text(
                    text = "تاريخ وموعد التنبيه",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { datePickerDialog.show() }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "اختر التاريخ",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formattedDateTime,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "اختر الوقت",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "اضغط لتعديل توقيت التنبيه بالدقة",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Voice TTS Announcement section
                Text(
                    text = "تخصيص التنبيه الناطق (اختياري)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = customAnnouncement,
                    onValueChange = { customAnnouncement = it },
                    label = { Text("مثال: حان الآن موعد الصلاة أو مقابلة العميل أحمد") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = TextStyle(textAlign = TextAlign.Right),
                )

                // Flash and speech activation switches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = useVoice,
                        onCheckedChange = { useVoice = it }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "نطق التنبيه صوتياً (صوت)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "تنبيه صوتي",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = useFlash,
                        onCheckedChange = { useFlash = it },
                        modifier = Modifier.testTag("toggle_flash")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "تفعيل وميض الكاميرا (فلاش)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "فلاش",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm and reject actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (title.trim().isNotEmpty()) {
                                val textToSpeak = customAnnouncement.ifEmpty { title }
                                onConfirm(
                                    title.trim(),
                                    description.trim(),
                                    timeStampState,
                                    useVoice,
                                    useFlash,
                                    textToSpeak.trim()
                                )
                            }
                        },
                        enabled = title.trim().isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_confirm_add"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حفظ الموعد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }

                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun OngoingAlarmOverlay(
    title: String,
    onDismiss: () -> Unit
) {
    // Elegant pulsing animation of the bell and active alert layout
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotateAngel by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotate"
    )

    Dialog(
        onDismissRequest = { /* Force stop button clicks rather than dismiss clicks */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmicDarkBackground.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
            ) {
                // Flashing Bell Icon
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .scale(0.85f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        GoldAccent.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Rotating ringing bell icon
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "منبه يرن",
                        tint = GoldAccent,
                        modifier = Modifier
                            .size(100.dp)
                            .scale(pulseScale)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "تنبيه هام ومجدول الآن!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "جاري الآن تكرار المنبه الصوتي لضمان سماعه، بالإضافة لوميض فلاش الهاتف لتسهيل الانتباه.",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Large Glowing Red STOP alarm button
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedStop),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .scale(pulseScale)
                        .testTag("btn_stop_alarm")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "وقف",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "إيقاف التنبيه نهائياً",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
