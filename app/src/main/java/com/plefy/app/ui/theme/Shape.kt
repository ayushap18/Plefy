package com.plefy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Anthropic radius scale: sm 6 (dropdown items), md 8 (buttons/inputs/tabs), lg 12 (cards),
// xl 16 (hero/large). Mapped onto Material 3's five shape slots.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // small inline / dropdown items
    small = RoundedCornerShape(8.dp),        // buttons, inputs, tabs, chips
    medium = RoundedCornerShape(12.dp),      // content / feature / product cards
    large = RoundedCornerShape(16.dp),       // hero, large marquee, bottom sheets
    extraLarge = RoundedCornerShape(20.dp),
)
