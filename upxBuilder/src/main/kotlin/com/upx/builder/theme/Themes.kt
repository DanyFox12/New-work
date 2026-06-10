package com.upx.builder.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A named editor + UI theme. [colorScheme] drives Material 3 surfaces, while the
 * [SyntaxPalette] colours code tokens in the editor — keeping the chrome and the
 * code consistent the way Android Studio's themes do.
 */
data class AppTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val colorScheme: ColorScheme,
    val syntax: SyntaxPalette,
    val editorBackground: Color,
    val gutterBackground: Color,
    val lineNumber: Color,
    val currentLineHighlight: Color,
    val accent: Color,
)

/** Colours used by the syntax highlighter for each token category. */
data class SyntaxPalette(
    val plain: Color,
    val keyword: Color,
    val type: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val function: Color,
)

object Themes {

    private fun darkScheme(primary: Color, secondary: Color, surface: Color, background: Color) =
        darkColorScheme(
            primary = primary,
            secondary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surface,
            onPrimary = Color(0xFF0A0A0A),
            onBackground = Color(0xFFE6E6E6),
            onSurface = Color(0xFFE0E0E0),
        )

    private fun lightScheme(primary: Color, secondary: Color, surface: Color, background: Color) =
        lightColorScheme(
            primary = primary,
            secondary = secondary,
            background = background,
            surface = surface,
            surfaceVariant = surface,
            onPrimary = Color.White,
            onBackground = Color(0xFF1A1A1A),
            onSurface = Color(0xFF202020),
        )

    /** 1 — Darcula, the classic IntelliJ / Android Studio dark look. */
    val darcula = AppTheme(
        id = "darcula", displayName = "Darcula", isDark = true,
        colorScheme = darkScheme(Color(0xFF4E94CE), Color(0xFFCC7832), Color(0xFF3C3F41), Color(0xFF2B2B2B)),
        syntax = SyntaxPalette(
            plain = Color(0xFFA9B7C6), keyword = Color(0xFFCC7832), type = Color(0xFF4EC9B0),
            string = Color(0xFF6A8759), number = Color(0xFF6897BB), comment = Color(0xFF808080),
            annotation = Color(0xFFBBB529), function = Color(0xFFFFC66D),
        ),
        editorBackground = Color(0xFF2B2B2B), gutterBackground = Color(0xFF313335),
        lineNumber = Color(0xFF606366), currentLineHighlight = Color(0xFF323232), accent = Color(0xFF4E94CE),
    )

    /** 2 — Midnight Ocean, a deep blue dark theme. */
    val ocean = AppTheme(
        id = "ocean", displayName = "Midnight Ocean", isDark = true,
        colorScheme = darkScheme(Color(0xFF26C6DA), Color(0xFF7E57C2), Color(0xFF132330), Color(0xFF0B1722)),
        syntax = SyntaxPalette(
            plain = Color(0xFFD6E2EC), keyword = Color(0xFF4FC1FF), type = Color(0xFF4EC9B0),
            string = Color(0xFF9CDCFE), number = Color(0xFFB5CEA8), comment = Color(0xFF5C7080),
            annotation = Color(0xFFDCDCAA), function = Color(0xFFC586C0),
        ),
        editorBackground = Color(0xFF0B1722), gutterBackground = Color(0xFF0F1E2B),
        lineNumber = Color(0xFF44607A), currentLineHighlight = Color(0xFF142838), accent = Color(0xFF26C6DA),
    )

    /** 3 — Dracula, the famous purple-pink dark palette. */
    val dracula = AppTheme(
        id = "dracula", displayName = "Dracula", isDark = true,
        colorScheme = darkScheme(Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF44475A), Color(0xFF282A36)),
        syntax = SyntaxPalette(
            plain = Color(0xFFF8F8F2), keyword = Color(0xFFFF79C6), type = Color(0xFF8BE9FD),
            string = Color(0xFFF1FA8C), number = Color(0xFFBD93F9), comment = Color(0xFF6272A4),
            annotation = Color(0xFF50FA7B), function = Color(0xFF50FA7B),
        ),
        editorBackground = Color(0xFF282A36), gutterBackground = Color(0xFF21222C),
        lineNumber = Color(0xFF6272A4), currentLineHighlight = Color(0xFF313442), accent = Color(0xFFBD93F9),
    )

    /** 4 — Monokai, warm high-contrast dark. */
    val monokai = AppTheme(
        id = "monokai", displayName = "Monokai", isDark = true,
        colorScheme = darkScheme(Color(0xFFA6E22E), Color(0xFFF92672), Color(0xFF3E3D32), Color(0xFF272822)),
        syntax = SyntaxPalette(
            plain = Color(0xFFF8F8F2), keyword = Color(0xFFF92672), type = Color(0xFF66D9EF),
            string = Color(0xFFE6DB74), number = Color(0xFFAE81FF), comment = Color(0xFF75715E),
            annotation = Color(0xFFA6E22E), function = Color(0xFFA6E22E),
        ),
        editorBackground = Color(0xFF272822), gutterBackground = Color(0xFF2D2E27),
        lineNumber = Color(0xFF90908A), currentLineHighlight = Color(0xFF3E3D32), accent = Color(0xFFA6E22E),
    )

    /** 5 — Nord, cool arctic dark. */
    val nord = AppTheme(
        id = "nord", displayName = "Nord", isDark = true,
        colorScheme = darkScheme(Color(0xFF88C0D0), Color(0xFF81A1C1), Color(0xFF3B4252), Color(0xFF2E3440)),
        syntax = SyntaxPalette(
            plain = Color(0xFFD8DEE9), keyword = Color(0xFF81A1C1), type = Color(0xFF8FBCBB),
            string = Color(0xFFA3BE8C), number = Color(0xFFB48EAD), comment = Color(0xFF616E88),
            annotation = Color(0xFFEBCB8B), function = Color(0xFF88C0D0),
        ),
        editorBackground = Color(0xFF2E3440), gutterBackground = Color(0xFF323846),
        lineNumber = Color(0xFF4C566A), currentLineHighlight = Color(0xFF3B4252), accent = Color(0xFF88C0D0),
    )

    /** 6 — Solarized Dark. */
    val solarizedDark = AppTheme(
        id = "solarized_dark", displayName = "Solarized Dark", isDark = true,
        colorScheme = darkScheme(Color(0xFF268BD2), Color(0xFFD33682), Color(0xFF073642), Color(0xFF002B36)),
        syntax = SyntaxPalette(
            plain = Color(0xFF93A1A1), keyword = Color(0xFF859900), type = Color(0xFFB58900),
            string = Color(0xFF2AA198), number = Color(0xFFD33682), comment = Color(0xFF586E75),
            annotation = Color(0xFFCB4B16), function = Color(0xFF268BD2),
        ),
        editorBackground = Color(0xFF002B36), gutterBackground = Color(0xFF073642),
        lineNumber = Color(0xFF586E75), currentLineHighlight = Color(0xFF073642), accent = Color(0xFF268BD2),
    )

    /** 7 — Synthwave, neon retro dark. */
    val synthwave = AppTheme(
        id = "synthwave", displayName = "Synthwave '84", isDark = true,
        colorScheme = darkScheme(Color(0xFFFF7EDB), Color(0xFF36F9F6), Color(0xFF2A2139), Color(0xFF1A1626)),
        syntax = SyntaxPalette(
            plain = Color(0xFFF8F8F2), keyword = Color(0xFFFF7EDB), type = Color(0xFF36F9F6),
            string = Color(0xFFFEDE5D), number = Color(0xFFF97E72), comment = Color(0xFF8E7CC3),
            annotation = Color(0xFF72F1B8), function = Color(0xFF36F9F6),
        ),
        editorBackground = Color(0xFF1A1626), gutterBackground = Color(0xFF241B30),
        lineNumber = Color(0xFF6C5C8C), currentLineHighlight = Color(0xFF2A2139), accent = Color(0xFFFF7EDB),
    )

    /** 8 — IntelliJ Light, clean default light theme. */
    val intellijLight = AppTheme(
        id = "intellij_light", displayName = "IntelliJ Light", isDark = false,
        colorScheme = lightScheme(Color(0xFF3573F0), Color(0xFF6F42C1), Color(0xFFFFFFFF), Color(0xFFF7F8FA)),
        syntax = SyntaxPalette(
            plain = Color(0xFF080808), keyword = Color(0xFF0033B3), type = Color(0xFF00627A),
            string = Color(0xFF067D17), number = Color(0xFF1750EB), comment = Color(0xFF8C8C8C),
            annotation = Color(0xFF9E880D), function = Color(0xFF00627A),
        ),
        editorBackground = Color(0xFFFFFFFF), gutterBackground = Color(0xFFF0F1F2),
        lineNumber = Color(0xFFAEB3B9), currentLineHighlight = Color(0xFFFCFAED), accent = Color(0xFF3573F0),
    )

    /** 9 — Solarized Light. */
    val solarizedLight = AppTheme(
        id = "solarized_light", displayName = "Solarized Light", isDark = false,
        colorScheme = lightScheme(Color(0xFF268BD2), Color(0xFFD33682), Color(0xFFFDF6E3), Color(0xFFEEE8D5)),
        syntax = SyntaxPalette(
            plain = Color(0xFF586E75), keyword = Color(0xFF859900), type = Color(0xFFB58900),
            string = Color(0xFF2AA198), number = Color(0xFFD33682), comment = Color(0xFF93A1A1),
            annotation = Color(0xFFCB4B16), function = Color(0xFF268BD2),
        ),
        editorBackground = Color(0xFFFDF6E3), gutterBackground = Color(0xFFEEE8D5),
        lineNumber = Color(0xFF93A1A1), currentLineHighlight = Color(0xFFEEE8D5), accent = Color(0xFF268BD2),
    )

    /** 10 — Sunset, a warm light theme. */
    val sunset = AppTheme(
        id = "sunset", displayName = "Sunset", isDark = false,
        colorScheme = lightScheme(Color(0xFFE8590C), Color(0xFFC2255C), Color(0xFFFFF4EC), Color(0xFFFFF9F4)),
        syntax = SyntaxPalette(
            plain = Color(0xFF3B2A22), keyword = Color(0xFFC2255C), type = Color(0xFF0B7285),
            string = Color(0xFF2B8A3E), number = Color(0xFF7048E8), comment = Color(0xFFB0A090),
            annotation = Color(0xFFE8590C), function = Color(0xFF1864AB),
        ),
        editorBackground = Color(0xFFFFF9F4), gutterBackground = Color(0xFFFFF0E6),
        lineNumber = Color(0xFFC9B6A6), currentLineHighlight = Color(0xFFFFEFE2), accent = Color(0xFFE8590C),
    )

    /** All themes, in display order. The user requested ten. */
    val all: List<AppTheme> = listOf(
        darcula, ocean, dracula, monokai, nord,
        solarizedDark, synthwave, intellijLight, solarizedLight, sunset,
    )

    val default: AppTheme = darcula

    fun byId(id: String): AppTheme = all.firstOrNull { it.id == id } ?: default
}
