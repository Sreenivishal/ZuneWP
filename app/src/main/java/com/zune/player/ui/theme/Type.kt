package com.zune.player.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zune.player.R

val ZuneFontFamily = FontFamily(
    Font(R.font.segoe_ui, FontWeight.Light),
    Font(R.font.segoe_ui, FontWeight.Normal)
)

val SegoeUiLightFontFamily = FontFamily(
    Font(R.font.segoe_ui_light)
)

val SegoeUiBoldFontFamily = FontFamily(
    Font(R.font.segoeuithibd, FontWeight.Bold)
)

val ZuneTypography = Typography(
    h1 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        letterSpacing = (-1.5).sp,
        color = ZuneTextPrimary
    ),
    h2 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        letterSpacing = (-0.5).sp,
        color = ZuneTextPrimary
    ),
    h3 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        letterSpacing = 0.25.sp,
        color = ZuneTextPrimary
    ),
    h4 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        letterSpacing = 0.15.sp,
        color = ZuneTextPrimary
    ),
    subtitle1 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        letterSpacing = 0.5.sp,
        color = ZuneTextSecondary
    ),
    body1 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        letterSpacing = 0.25.sp,
        color = ZuneTextPrimary
    ),
    body2 = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 1.25.sp,
        color = ZuneTextSecondary
    ),
    button = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
        color = ZuneTextPrimary
    ),
    caption = TextStyle(
        fontFamily = ZuneFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        color = ZuneTextSecondary
    )
)
