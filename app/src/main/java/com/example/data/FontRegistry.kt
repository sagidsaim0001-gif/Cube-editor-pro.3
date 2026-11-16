package com.example.data

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.example.R

data class BundledFont(
    val id: String,
    val displayName: String,
    val category: String,
    @FontRes val resId: Int,
    val supportsDevanagari: Boolean = false,
    val supportsArabicUrdu: Boolean = false
)

object FontRegistry {
    val fonts: List<BundledFont> = listOf(
        BundledFont("anton", "Anton", "Heavy Display", R.font.anton),
        BundledFont("bebas_neue", "Bebas Neue", "Condensed Bold", R.font.bebas_neue),
        BundledFont("oswald", "Oswald", "Condensed", R.font.oswald),
        BundledFont("syne", "Syne", "Avant Display", R.font.syne),
        BundledFont("montserrat", "Montserrat", "Grotesque", R.font.montserrat),
        BundledFont("space_grotesk", "Space Grotesk", "Geometric", R.font.space_grotesk),
        BundledFont("plus_jakarta_sans", "Plus Jakarta", "Clean Sans", R.font.plus_jakarta_sans),
        BundledFont("inter", "Inter", "Neutral UI", R.font.inter),
        BundledFont("playfair_display", "Playfair", "Serif Display", R.font.playfair_display),
        BundledFont("pacifico", "Pacifico", "Handwriting", R.font.pacifico),
        BundledFont("jetbrains_mono", "JetBrains Mono", "Monospace", R.font.jetbrains_mono),
        BundledFont("noto_sans_devanagari", "Noto Hindi", "Devanagari", R.font.noto_sans_devanagari, supportsDevanagari = true),
        BundledFont("noto_naskh_arabic", "Noto Urdu", "Arabic / Urdu", R.font.noto_naskh_arabic, supportsArabicUrdu = true)
    )

    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val composeFamilyCache = mutableMapOf<String, FontFamily>()

    fun findById(id: String): BundledFont = fonts.firstOrNull { it.id == id } ?: fonts.first()

    fun getTypeface(context: Context, fontId: String, textHint: String = ""): Typeface {
        val effectiveId = resolveScriptFallback(fontId, textHint)
        return typefaceCache.getOrPut(effectiveId) {
            val font = findById(effectiveId)
            ResourcesCompat.getFont(context, font.resId) ?: Typeface.DEFAULT_BOLD
        }
    }

    fun getComposeFontFamily(fontId: String): FontFamily {
        return composeFamilyCache.getOrPut(fontId) {
            FontFamily(Font(findById(fontId).resId))
        }
    }

    private fun resolveScriptFallback(fontId: String, text: String): String {
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari && !findById(fontId).supportsDevanagari) {
            return "noto_sans_devanagari"
        }
        val hasArabic = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }
        if (hasArabic && !findById(fontId).supportsArabicUrdu) {
            return "noto_naskh_arabic"
        }
        return fontId
    }
}
