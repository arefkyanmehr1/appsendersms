package com.example.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DocItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val badge: String? = null,
    val iconTint: Color,
    val iconBg: Color,
    val summary: String,
    val stepsOrPoints: List<String>,
    val tips: String? = null
)

@Composable
fun DocumentationSection(
    modifier: Modifier = Modifier
) {
    val docItems = remember {
        listOf(
            DocItem(
                id = "play_protect",
                title = "حل اخطار امنیتی موقع نصب (Play Protect)",
                icon = Icons.Default.Security,
                badge = "بسیار مهم",
                iconTint = Color(0xFFE65100),
                iconBg = Color(0xFFFFE0B2),
                summary = "چرا هنگام نصب هشدار Unsafe App یا Blocked by Play Protect نمایش داده می‌شود و نحوه رفع آن چیست؟",
                stepsOrPoints = listOf(
                    "۱. دلیل هشدار: این اپلیکیشن مخصوص پذیرندگان پرداخت است و با کلید امنیتی اختصاصی امضا شده و خارج از استور رسمی گوگل پلی به صورت مستقیم نصب می‌گردد. همچنین به مجوز دریافت پیامک بانکی برای تسویه خودکار نیاز دارد.",
                    "۲. نحوه نصب: هنگام مشاهده پنجره هشدار سپر ایمنی گوگل (Play Protect)، روی گزینه «جزئیات بیشتر» یا «More details» ضربه بزنید.",
                    "۳. تایید نصب: سپس روی دکمه «به هر حال نصب شود» یا «Install anyway» کلیک کنید تا نصب با موفقیت تکمیل شود.",
                    "۴. تضمین امنیت: تمامی کلیدها و داده‌ها در تراشه سخت‌افزاری دستگاه (Android KeyStore) با استاندارد نظامی AES-256 GCM رمزگذاری شده و پیامک‌ها هرگز برای هیچ سرور واسط متفرقه‌ای ارسال نمی‌شوند."
                ),
                tips = "پیشنهاد: پس از نصب، وارد تنظیمات گوشی و بخش Apps شوید و به PayLink اجازه دسترسی به SMS و اعلان‌ها را تایید نمایید."
            ),
            DocItem(
                id = "simultaneous_fifo",
                title = "سفارش‌های همزمان و مبالغ یکسان (FIFO)",
                icon = Icons.Default.Sync,
                badge = "هوشمند",
                iconTint = Color(0xFF1976D2),
                iconBg = Color(0xFFBBDEFB),
                summary = "نحوه رفتار سیستم زمانی که چند مشتری سفارش با مبالغ کاملاً یکسان در یک بازه زمانی ثبت می‌کنند.",
                stepsOrPoints = listOf(
                    "۱. صف امن غیرهمگام (Mutex Queue): تمام پیامک‌های ورودی بانک در یک خط‌لوله تک‌به‌تک و ایزوله صف‌بندی می‌شوند تا هیچ تداخلی بین سفارش‌ها رخ ندهد.",
                    "۲. اولویت بر اساس تقدم زمان ثبت (FIFO): اگر ۳ سفارش مثلاً ۳۰۰ هزار تومانی همزمان در انتظار باشند، با رسیدن اولین پیامک واریز، قدیمی‌ترین سفارش ثبت‌شده تایید و تسویه می‌گردد.",
                    "۳. تفکیک دقیق: بلافاصله پس از تسویه سفارش اول، از صف فعال خارج شده و پیامک بعدی فاکتور بعدی را تسویه خواهد کرد."
                ),
                tips = "این مکانیزم خطر خطای انسانی یا سوختن سفارش مشتریان همزمان را به صفر می‌رساند."
            ),
            DocItem(
                id = "card_and_currency",
                title = "تطبیق شماره کارت و تومان/ریال",
                icon = Icons.Default.CreditCard,
                iconTint = Color(0xFF388E3C),
                iconBg = Color(0xFFC8E6C9),
                summary = "چگونه سیستم ۴ رقم کارت و تفاوت واحدهای تومان و ریال را به صورت خودکار شناسایی می‌کند؟",
                stepsOrPoints = listOf(
                    "۱. اولویت ۴ رقم آخر کارت: در صورتی که مشتری شماره کارت خود را در توضیحات سفارش قید کرده باشد و پیامک بانک هم حاوی ۴ رقم کارت باشد، سیستم مستقیماً به همان سفارش متصل می‌شود حتی اگر سفارش جدیدتر باشد.",
                    "۲. ضریب تبدیل تومان به ریال: مبالغ پیامک‌های بانکی معمولاً به ریال است. سیستم به صورت چندمرحله‌ای هم مبالغ ریالی و هم در صورت نیاز معادل تومانی (ضریب ۱۰) را تحلیل و تطبیق می‌دهد."
                ),
                tips = "توصیه می‌شود در فروشگاه یا ربات خود از مشتریان بخواهید در صورت امکان ۴ رقم آخر کارت واریزی را قید کنند."
            ),
            DocItem(
                id = "grace_window",
                title = "مهلت پرداخت و بازه ارفاق تاخیر پیامک",
                icon = Icons.Default.HourglassTop,
                iconTint = Color(0xFF7B1FA2),
                iconBg = Color(0xFFE1BEE7),
                summary = "پشتیبانی از تاخیرهای مخابراتی بانک تا ۵ دقیقه پس از اتمام تایمر فاکتور.",
                stepsOrPoints = listOf(
                    "۱. تایمر فاکتور: هر سفارش دارای یک زمان انقضای مشخص (مثلاً ۱۵ یا ۳۰ دقیقه) است.",
                    "۲. بازه ارفاق (Grace Window): به دلیل ترافیک اپراتورهای همراه، گاهی پیامک بانک ۳ تا ۵ دقیقه دیرتر ارسال می‌شود. موتور تطبیق هوشمند حتی تا ۵ دقیقه (۳۰۰ ثانیه) پس از انقضای تایمر، پیامک واریزی را پذیرفته و سفارش را به جای سوختن، تایید می‌کند."
                ),
                tips = "زمان انقضای هر فاکتور را می‌توانید از بخش تنظیمات زمان در بالای همین صفحه تغییر دهید."
            ),
            DocItem(
                id = "reject_logic",
                title = "رد و لغو سفارش و وب‌هوک خودکار",
                icon = Icons.Default.Cancel,
                iconTint = Color(0xFFD32F2F),
                iconBg = Color(0xFFFFCDD2),
                summary = "چه اتفاقی با زدن دکمه «رد و لغو سفارش» می‌افتد؟",
                stepsOrPoints = listOf(
                    "۱. حذف آنی و محلی: فاکتور فوراً از لیست فاکتورهای فعال حذف شده و در جدول سفارش‌های لغو شده محلی ذخیره می‌شود تا دیگر برای پردازش پیامک تداخل ایجاد نکند.",
                    "۲. مخابره وب‌هوک به فروشگاه: درخواست لغو به سرور ارسال شده و وب‌هوک شکست برای ووکامرس یا ربات فروشگاهی ارسال می‌شود تا کالا در انبار آزاد شود."
                ),
                tips = "از این دکمه در مواردی که مشتری از خرید منصرف شده یا زمان پرداخت او گذشته استفاده کنید."
            ),
            DocItem(
                id = "battery_optimization",
                title = "بهینه‌سازی باتری (شیائومی، سامسونگ، هواوی)",
                icon = Icons.Default.BatteryChargingFull,
                iconTint = Color(0xFF00796B),
                iconBg = Color(0xFFB2DFDB),
                summary = "جلوگیری از بسته شدن خودکار سرویس پیامک توسط اندروید در پس‌زمینه.",
                stepsOrPoints = listOf(
                    "۱. شیائومی (MIUI / HyperOS): وارد App Info برنامه شده، گزینه Autostart را فعال کرده و Battery Saver را روی «No restrictions» بگذارید.",
                    "۲. سامسونگ (One UI): در بخش Battery، برنامه PayLink را به لیست «Never sleeping apps» اضافه کنید.",
                    "۳. قفل برنامه در اخیرها: برنامه را در منوی Recent Apps قفل (Lock) کنید تا رم گوشی آن را نبندد."
                ),
                tips = "سرویس مانیتورینگ پی‌لینک به گونه‌ای بهینه شده که کمتر از ۱ درصد شارژ باتری در روز مصرف می‌کند."
            )
        )
    }

    var expandedItemId by remember { mutableStateOf<String?>("play_protect") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "راهنما، مستندات و حل مشکلات",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "توضیح انواع حالت‌های سفارش، خطاهای ممکن و رفع ارور نصب",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        docItems.forEach { item ->
            val isExpanded = expandedItemId == item.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        expandedItemId = if (isExpanded) null else item.id
                    },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpanded) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 3.dp else 1.dp),
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(item.iconBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.badge != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = item.iconTint.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = item.badge,
                                                color = item.iconTint,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isExpanded) 4 else 1
                                )
                            }
                        }

                        IconButton(
                            onClick = { expandedItemId = if (isExpanded) null else item.id },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "بستن" else "باز کردن",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            item.stepsOrPoints.forEach { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(6.dp)
                                            .background(item.iconTint, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (item.tips != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = item.iconBg.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, item.iconTint.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.tips,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = item.iconTint,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
