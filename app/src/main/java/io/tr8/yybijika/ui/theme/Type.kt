package io.tr8.yybijika.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography()

// Hanzi need far more optical size than latin text to stay legible, and pinyin
// sits above them at roughly a third of their size. These are shared by every
// exercise so a character is the same size wherever it appears.
val HanziHero = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 64.sp,
    lineHeight = 76.sp,
)

val HanziMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 32.sp,
    lineHeight = 44.sp,
)

val HanziInline = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 22.sp,
    lineHeight = 34.sp,
)

val PinyinStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp,
)
