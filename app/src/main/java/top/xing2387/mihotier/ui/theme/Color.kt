package top.xing2387.mihotier.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Legacy / Clash token aliases
val ClashPrimary = Color(0xFF234B7C)
val ClashPrimaryDark = Color(0xFF163254)
val ClashAccent = Color(0xFF3B7BBF)
val ClashBackground = Color(0xFFF5F5F5)
val ClashSurface = Color(0xFFFFFFFF)
val ClashTextPrimary = Color(0xFF212121)
val ClashTextSecondary = Color(0xFF757575)
val ClashDivider = Color(0xFFE0E0E0)

/**
 * Unified semantic design tokens for MihoTier & EasyTier screens.
 * Supports clean Dark Mode and Light Mode with high-contrast text and cohesive brand accents.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val surfaceVariant: Color,
    val surfaceSubtle: Color,
    val innerBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val brandPrimary: Color,
    val brandPrimaryDark: Color,
    val tabSelectedBg: Color,
    val tabSelectedText: Color,
    val tabUnselectedText: Color,
    val statusSuccessBg: Color,
    val statusSuccessText: Color,
    val statusSuccessBorder: Color,
    val statusDangerBg: Color,
    val statusDangerText: Color,
    val statusWarningBg: Color,
    val statusWarningText: Color,
    val statusInfoBg: Color,
    val statusInfoText: Color,
    val statusPurpleBg: Color,
    val statusPurpleText: Color,
    val divider: Color,
    val terminalBg: Color,
    val terminalText: Color
)

val LightAppColors = AppColors(
    isDark = false,
    background = Color(0xFFEEF3F8),
    cardBackground = Color.White,
    cardBorder = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFF1F5F9),
    surfaceSubtle = Color(0xFFF8FAFC),
    innerBorder = Color(0xFFCBD5E1),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    textMuted = Color(0xFF94A3B8),
    brandPrimary = Color(0xFF104179),
    brandPrimaryDark = Color(0xFF0D325E),
    tabSelectedBg = Color(0xFFBAE6FD),
    tabSelectedText = Color(0xFF075985),
    tabUnselectedText = Color(0xFF64748B),
    statusSuccessBg = Color(0xFFD1FAE5),
    statusSuccessText = Color(0xFF065F46),
    statusSuccessBorder = Color(0xFF6EE7B7),
    statusDangerBg = Color(0xFFFEE2E2),
    statusDangerText = Color(0xFFDC2626),
    statusWarningBg = Color(0xFFFEF3C7),
    statusWarningText = Color(0xFFB45309),
    statusInfoBg = Color(0xFFE0F2FE),
    statusInfoText = Color(0xFF0369A1),
    statusPurpleBg = Color(0xFFEDE9FE),
    statusPurpleText = Color(0xFF6D28D9),
    divider = Color(0xFFF1F5F9),
    terminalBg = Color(0xFF0F172A),
    terminalText = Color(0xFFE2E8F0)
)

val DarkAppColors = AppColors(
    isDark = true,
    background = Color(0xFF0B111D),
    cardBackground = Color(0xFF162032),
    cardBorder = Color(0xFF26354A),
    surfaceVariant = Color(0xFF1E2B3E),
    surfaceSubtle = Color(0xFF121B29),
    innerBorder = Color(0xFF2C3E55),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    brandPrimary = Color(0xFF3B82F6),
    brandPrimaryDark = Color(0xFF1D4ED8),
    tabSelectedBg = Color(0xFF1E3A5F),
    tabSelectedText = Color(0xFF38BDF8),
    tabUnselectedText = Color(0xFF94A3B8),
    statusSuccessBg = Color(0xFF064E3B),
    statusSuccessText = Color(0xFF34D399),
    statusSuccessBorder = Color(0xFF059669),
    statusDangerBg = Color(0xFF7F1D1D),
    statusDangerText = Color(0xFFF87171),
    statusWarningBg = Color(0xFF78350F),
    statusWarningText = Color(0xFFFBBF24),
    statusInfoBg = Color(0xFF0C4A6E),
    statusInfoText = Color(0xFF38BDF8),
    statusPurpleBg = Color(0xFF3B0764),
    statusPurpleText = Color(0xFFC084FC),
    divider = Color(0xFF1E293B),
    terminalBg = Color(0xFF070B12),
    terminalText = Color(0xFFE2E8F0)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

/**
 * Text field colors with proper contrast in both light and dark themes.
 * Prevents input text and labels from becoming invisible white-on-white.
 */
@Composable
fun appTextFieldColors(): TextFieldColors {
    val colors = AppTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textMuted,
        focusedBorderColor = colors.brandPrimary,
        unfocusedBorderColor = colors.innerBorder,
        disabledBorderColor = colors.innerBorder.copy(alpha = 0.5f),
        focusedLabelColor = colors.brandPrimary,
        unfocusedLabelColor = colors.textSecondary,
        disabledLabelColor = colors.textMuted,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted,
        disabledPlaceholderColor = colors.textMuted.copy(alpha = 0.5f),
        focusedTrailingIconColor = colors.textSecondary,
        unfocusedTrailingIconColor = colors.textMuted,
        cursorColor = colors.brandPrimary
    )
}