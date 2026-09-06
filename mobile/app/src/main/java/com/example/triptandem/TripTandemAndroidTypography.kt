package com.triptandem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.triptandem.shared.createTripTandemTypography

internal fun tripTandemAndroidTypography() = createTripTandemTypography(
    cabinetBold = FontFamily(Font(R.font.cabinet_grotesk_bold, FontWeight.Bold)),
    cabinetExtraBold = FontFamily(Font(R.font.cabinet_grotesk_extra_bold, FontWeight.ExtraBold)),
    satoshi = FontFamily(
        Font(R.font.satoshi_regular, FontWeight.Normal),
        Font(R.font.satoshi_medium, FontWeight.Medium),
        Font(R.font.satoshi_bold, FontWeight.Bold),
    ),
)
