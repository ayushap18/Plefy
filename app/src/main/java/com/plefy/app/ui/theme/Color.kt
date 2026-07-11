package com.plefy.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// Anthropic editorial brand palette — cream canvas + coral voltage + dark-navy chrome.
// Raw brand tokens (for the few bespoke on-brand touches) + Material 3 role mapping.
// ---------------------------------------------------------------------------------------------

// Brand
val BrandCoral = Color(0xFFCC785C)        // primary — CTAs, brand voltage
val BrandTeal = Color(0xFF5DB8A6)         // status / active-connection dots
val BrandAmber = Color(0xFFE8A55A)        // category badges, highlights

// Surface (cream family) + dark chrome
val Canvas = Color(0xFFFAF9F5)            // page floor — tinted cream, not pure white
val SurfaceSoft = Color(0xFFF5F0E8)       // soft bands, dividers
val SurfaceCard = Color(0xFFEFE9DE)       // content/feature cards
val SurfaceDark = Color(0xFF181715)       // code/model/footer surfaces
val SurfaceDarkElevated = Color(0xFF252320)
val Hairline = Color(0xFFE6DFD8)          // 1px borders on cream

// Text / ink
val Ink = Color(0xFF141413)               // headlines, primary text (warm dark)
val Body = Color(0xFF3D3D3A)              // running text
val Muted = Color(0xFF6C6A64)             // sub-heads, breadcrumbs
val OnDark = Color(0xFFFAF9F5)            // cream-white on dark
val OnDarkSoft = Color(0xFFA09D96)

val BrandError = Color(0xFFC64545)
val OnError = Color(0xFFFFFFFF)

// ---- Light (default editorial look) — M3 roles ----
val LightPrimary = BrandCoral
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFF6DDD1)
val LightOnPrimaryContainer = Color(0xFF5A2A17)
val LightSecondary = Color(0xFF2E877A)             // deepened teal for AA text/icons on cream
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCDEAE2)
val LightOnSecondaryContainer = Color(0xFF06231E)
val LightTertiary = Color(0xFFB4702A)              // deepened amber for AA
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFBE7CC)
val LightOnTertiaryContainer = Color(0xFF4A2F0A)
val LightBackground = Canvas
val LightOnBackground = Ink
val LightSurface = Canvas
val LightOnSurface = Ink
val LightSurfaceVariant = SurfaceSoft
val LightOnSurfaceVariant = Muted
val LightOutline = Color(0xFFB8B1A6)
val LightOutlineVariant = Hairline
val LightError = BrandError
val LightOnError = OnError
val LightInverseSurface = SurfaceDark
val LightInverseOnSurface = OnDark
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = SurfaceSoft
val LightSurfaceContainer = SurfaceCard
val LightSurfaceContainerHigh = Color(0xFFE9E2D6)
val LightSurfaceContainerHighest = Color(0xFFE3DCCF)

// ---- Dark (navy chrome) — M3 roles ----
val DarkPrimary = Color(0xFFE1997E)                // lifted coral for contrast on navy
val DarkOnPrimary = Color(0xFF3A1508)
val DarkPrimaryContainer = Color(0xFF7A3E29)
val DarkOnPrimaryContainer = Color(0xFFF6DDD1)
val DarkSecondary = BrandTeal
val DarkOnSecondary = Color(0xFF05231D)
val DarkSecondaryContainer = Color(0xFF184E44)
val DarkOnSecondaryContainer = Color(0xFFCDEAE2)
val DarkTertiary = BrandAmber
val DarkOnTertiary = Color(0xFF3D2609)
val DarkTertiaryContainer = Color(0xFF6B4418)
val DarkOnTertiaryContainer = Color(0xFFFBE7CC)
val DarkBackground = SurfaceDark
val DarkOnBackground = OnDark
val DarkSurface = SurfaceDark
val DarkOnSurface = OnDark
val DarkSurfaceVariant = SurfaceDarkElevated
val DarkOnSurfaceVariant = OnDarkSoft
val DarkOutline = Color(0xFF48443F)
val DarkOutlineVariant = Color(0xFF2E2B27)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkInverseSurface = Canvas
val DarkInverseOnSurface = Ink
val DarkSurfaceContainerLowest = Color(0xFF121110)
val DarkSurfaceContainerLow = Color(0xFF1F1D1B)
val DarkSurfaceContainer = SurfaceDarkElevated
val DarkSurfaceContainerHigh = Color(0xFF302D29)
val DarkSurfaceContainerHighest = Color(0xFF3B3833)
