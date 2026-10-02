package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.keeper.data.KeeperPreferences
import com.example.keeper.service.KeepAliveService
import com.example.keeper.trigger.FcmTrigger
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

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
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Current active running state
    var isEnabled by remember { mutableStateOf(KeeperPreferences.isKeepAliveEnabled(context)) }

    // Saved Preferences
    var savedMode by remember { mutableStateOf(KeeperPreferences.getOperationMode(context)) }
    var savedInterval by remember { mutableIntStateOf(KeeperPreferences.getIntervalMinutes(context)) }

    // Draft / Pending Configuration (for explicit Save button)
    var selectedMode by remember { mutableStateOf(savedMode) }
    var selectedInterval by remember { mutableIntStateOf(savedInterval) }

    // Check System Permissions & Optimization States
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun checkExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else true
    }

    fun checkBatteryOptimization(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else true
    }

    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission()) }
    var canScheduleExactAlarm by remember { mutableStateOf(checkExactAlarmPermission()) }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(checkBatteryOptimization()) }

    val refreshPermissions = {
        hasNotificationPermission = checkNotificationPermission()
        canScheduleExactAlarm = checkExactAlarmPermission()
        isIgnoringBatteryOptimizations = checkBatteryOptimization()
    }

    // Refresh permission states whenever the activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Permission Launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            scope.launch { snackbarHostState.showSnackbar("Đã cấp quyền thông báo thành công") }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCyan.copy(alpha = 0.15f))
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Checkin Keeper Logo",
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Checkin Keeper",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Card 1: Trạng thái (Đang chạy hoặc Chưa chạy, không cần đồng hồ)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isEnabled) CyberCyan.copy(alpha = 0.35f) else DarkSurfaceBorder
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "TRẠNG THÁI",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isEnabled) EmeraldStatus.copy(alpha = 0.15f)
                                    else RoseError.copy(alpha = 0.12f)
                                )
                                .border(
                                    1.dp,
                                    if (isEnabled) EmeraldStatus.copy(alpha = 0.5f)
                                    else RoseError.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isEnabled) EmeraldStatus else RoseError)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isEnabled) "Đang chạy" else "Chưa chạy",
                                color = if (isEnabled) EmeraldStatus else RoseError,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Card 2: Cấu hình hoạt động (Có nút Lưu cấu hình)
            item {
                var modeDropdownExpanded by remember { mutableStateOf(false) }
                var intervalDropdownExpanded by remember { mutableStateOf(false) }

                val modeDisplayName = if (selectedMode == KeeperPreferences.MODE_FOREGROUND_SERVICE) {
                    "Dịch vụ thường trực (Foreground Service)"
                } else {
                    "Chỉ báo thức hệ thống (Alarm Only)"
                }

                val hasUnsavedChanges = (selectedMode != savedMode) || (selectedInterval != savedInterval)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("configuration_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "CẤU HÌNH HOẠT ĐỘNG",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dropdown chọn Cách hoạt động
                        Text(
                            text = "Cách hoạt động",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dropdown_mode")
                                    .clickable { modeDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = DarkSurfaceElevated),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = modeDisplayName,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Chọn cách hoạt động",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = modeDropdownExpanded,
                                onDismissRequest = { modeDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Dịch vụ thường trực (Foreground Service)", fontSize = 13.sp) },
                                    onClick = {
                                        selectedMode = KeeperPreferences.MODE_FOREGROUND_SERVICE
                                        modeDropdownExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Chỉ báo thức hệ thống (Alarm Only)", fontSize = 13.sp) },
                                    onClick = {
                                        selectedMode = KeeperPreferences.MODE_ALARM_ONLY
                                        modeDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dropdown Tự chạy mỗi
                        Text(
                            text = "Tự chạy mỗi",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box {
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dropdown_interval")
                                    .clickable { intervalDropdownExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = DarkSurfaceElevated),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Chu kỳ",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$selectedInterval phút",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Chọn chu kỳ",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = intervalDropdownExpanded,
                                onDismissRequest = { intervalDropdownExpanded = false }
                            ) {
                                listOf(5, 10, 15).forEach { minutes ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "$minutes phút",
                                                fontWeight = if (minutes == selectedInterval) FontWeight.Bold else FontWeight.Normal,
                                                color = if (minutes == selectedInterval) CyberCyan else TextPrimary
                                            )
                                        },
                                        onClick = {
                                            selectedInterval = minutes
                                            intervalDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Nút LƯU CẤU HÌNH (Lưu đúng chế độ chạy)
                        Button(
                            onClick = {
                                KeeperPreferences.setOperationMode(context, selectedMode)
                                KeeperPreferences.setIntervalMinutes(context, selectedInterval)
                                savedMode = selectedMode
                                savedInterval = selectedInterval

                                // If keeper is active, apply the new configuration immediately
                                if (isEnabled) {
                                    if (selectedMode == KeeperPreferences.MODE_FOREGROUND_SERVICE) {
                                        KeepAliveService.start(context)
                                    } else {
                                        KeepAliveService.stop(context)
                                        KeepAliveService.scheduleNextAlarm(context, selectedInterval * 60 * 1000L)
                                    }
                                    KeepAliveService.scheduleNextAlarm(context, selectedInterval * 60 * 1000L)
                                }

                                scope.launch {
                                    snackbarHostState.showSnackbar("Đã lưu cấu hình hoạt động thành công")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_save_config"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hasUnsavedChanges) CyberCyan else CyberCyan.copy(alpha = 0.85f),
                                contentColor = Color(0xFF00363D)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Lưu",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LƯU CẤU HÌNH",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Nút KHỞI ĐỘNG LẠI NGAY
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = FcmTrigger.forceReconnect(context)
                                    KeeperPreferences.recordTriggerEvent(
                                        context,
                                        result.success,
                                        result.message,
                                        result.networkType
                                    )
                                    if (isEnabled) {
                                        KeepAliveService.scheduleNextAlarm(
                                            context,
                                            savedInterval * 60 * 1000L
                                        )
                                        KeepAliveService.updateNotification(context)
                                    }
                                    val msg = if (result.success) {
                                        "Đã gửi tín hiệu GMS Reconnect & Heartbeat thành công!"
                                    } else {
                                        "Kích hoạt: ${result.message}"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_trigger_now"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Khởi động lại ngay",
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "KHỞI ĐỘNG LẠI NGAY",
                                color = CyberCyan,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Card 3: Cài đặt (Toggle Luôn chạy nền & Các quyền CHƯA CẤP)
            item {
                val hasAnyUngrantedPermissions = !hasNotificationPermission ||
                        !canScheduleExactAlarm ||
                        !isIgnoringBatteryOptimizations

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkSurfaceBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "CÀI ĐẶT",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Toggle Luôn chạy nền
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Luôn chạy nền",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    isEnabled = checked
                                    KeeperPreferences.setKeepAliveEnabled(context, checked)
                                    if (checked) {
                                        if (savedMode == KeeperPreferences.MODE_FOREGROUND_SERVICE) {
                                            KeepAliveService.start(context)
                                        } else {
                                            KeepAliveService.scheduleNextAlarm(context, savedInterval * 60 * 1000L)
                                        }
                                        scope.launch { snackbarHostState.showSnackbar("Đã bật dịch vụ") }
                                    } else {
                                        KeepAliveService.stop(context)
                                        KeepAliveService.cancelAlarm(context)
                                        scope.launch { snackbarHostState.showSnackbar("Đã tắt dịch vụ") }
                                    }
                                },
                                modifier = Modifier.testTag("toggle_keep_alive"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00363D),
                                    checkedTrackColor = CyberCyan,
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = DarkSurfaceBorder
                                )
                            )
                        }

                        // Danh sách quyền CHƯA CẤP (các quyền đã cấp sẽ được ẩn hoàn toàn)
                        if (hasAnyUngrantedPermissions) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "QUYỀN CẦN THIẾT",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Quyền 1: Thông báo (Chỉ hiện khi chưa cấp)
                            if (!hasNotificationPermission) {
                                UngrantedPermissionRow(
                                    title = "Quyền hiển thị Thông báo",
                                    buttonText = "Cấp quyền",
                                    onAction = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Quyền 2: Báo thức chính xác (Chỉ hiện khi chưa cấp)
                            if (!canScheduleExactAlarm) {
                                UngrantedPermissionRow(
                                    title = "Báo thức chính xác (Exact Alarm)",
                                    buttonText = "Mở cài đặt",
                                    onAction = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            try {
                                                val intent = Intent(
                                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Không thể mở cài đặt: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Quyền 3: Bỏ qua tối ưu hóa Pin (Chỉ hiện khi chưa cấp)
                            if (!isIgnoringBatteryOptimizations) {
                                UngrantedPermissionRow(
                                    title = "Bỏ qua tối ưu hóa Pin",
                                    buttonText = "Yêu cầu",
                                    onAction = {
                                        requestBatteryOptimization(context)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Hiển thị một quyền hệ thống chưa được cấp.
 * Sau khi được cấp thì quyền này sẽ bị ẩn đi hoàn toàn khỏi giao diện.
 */
@Composable
fun UngrantedPermissionRow(
    title: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        FilledTonalButton(
            onClick = onAction,
            modifier = Modifier.height(34.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(text = buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@SuppressLint("BatteryLife")
fun requestBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Không thể mở cài đặt pin: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
