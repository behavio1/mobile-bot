package one.behavio.mobilebotui.ui

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Data-only theme imports; never loads executable code or external fonts. */
object CustomThemes {
    private fun cache(context: Context) = AtomicFile(File(context.filesDir, "custom-themes.json"))
    fun load(context: Context) {
        runCatching { decode(String(cache(context).readFully())) }.onSuccess(::register)
    }
    fun install(context: Context, json: String) {
        val themes = runCatching { decode(json) }.getOrElse {
            Log.w("MobileBotThemes", "theme_import_rejected"); return
        }
        if (GraphicTheme.entries.filter { it.customDefinition != null } == themes) return
        val file = cache(context)
        val stream = file.startWrite()
        try { stream.write(json.toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); Log.w("MobileBotThemes", "theme_cache_failed"); return }
        register(themes)
    }
    private fun register(themes: List<GraphicTheme>) {
        if (GraphicTheme.entries.toList() == GraphicTheme.builtIns + themes) return
        GraphicTheme.entries.clear(); GraphicTheme.entries.addAll(GraphicTheme.builtIns + themes)
    }
    internal fun decode(json: String): List<GraphicTheme> {
        require(json.length <= 3_300_000)
        val array = JSONArray(json); require(array.length() <= 50)
        val result = (0 until array.length()).map { decodeTheme(array.getJSONObject(it)) }
        require(result.map { it.storedId }.toSet().size == result.size)
        return result
    }
    private fun decodeTheme(t: JSONObject): GraphicTheme {
        require(t.getInt("schemaVersion") == 1)
        val id = t.getString("id"); require(id.matches(Regex("custom-[a-z0-9-]{1,48}")))
        fun text(key: String, max: Int): String = t.getString(key).also { require(it.isNotBlank() && it.length <= max) }
        val name = text("name",160); val description = text("description",160); text("prompt",8000)
        val base = GraphicTheme.builtIns.first { it.storedId == t.getString("artworkTheme") }
        fun color(p: JSONObject, key: String): Long = p.getString(key).also { require(it.matches(Regex("#[0-9a-fA-F]{6}"))) }.drop(1).toLong(16) or 0xFF000000
        fun palette(mode: String) = t.getJSONObject(mode).let { p ->
            themePalette(mode == "dark", color(p,"primary"),color(p,"container"),color(p,"accent"),
                color(p,"background"),color(p,"surface"),color(p,"raised"),color(p,"ink"),color(p,"muted")).also { c ->
                listOf(c.onSurface to c.surface,c.onBackground to c.background,c.onPrimary to c.primary,
                    c.onPrimaryContainer to c.primaryContainer,c.onSurfaceVariant to c.surfaceVariant,
                    c.onSecondary to c.secondary).forEach { (a,b) -> require(contrast(a,b) >= 4.5f) }
            }
        }
        fun number(o:JSONObject,key:String,min:Double,max:Double):Float = o.getDouble(key).also { require(it.isFinite() && it in min..max) }.toFloat()
        val font=t.getJSONObject("typography")
        val family=when(font.getString("family")){"sans"->FontFamily.SansSerif;"serif"->FontFamily.Serif;"mono"->FontFamily.Monospace;else->error("font")}
        val weight=number(font,"weight",400.0,900.0); require(weight % 100 == 0f)
        val shape=t.getJSONObject("shapes"); val card=t.getJSONObject("card")
        val definition=ThemeDefinition(palette("light"),palette("dark"),
            themeTypography(family,FontWeight(weight.toInt()),number(font,"tracking",-0.5,1.0)),
            themeShapes(number(shape,"small",0.0,36.0).toInt(),number(shape,"medium",0.0,36.0).toInt(),number(shape,"large",0.0,36.0).toInt()),
            AgentCardStyle(CardTreatment.valueOf(card.getString("treatment")),number(card,"radius",0.0,36.0).dp,
                number(card,"border",0.0,3.0).dp,number(card,"elevation",0.0,8.0).dp,
                number(card,"inset",0.0,8.0).dp,number(card,"gridGap",8.0,24.0).dp),base.definition.artwork)
        return GraphicTheme(id,name,description,definition)
    }
    private fun contrast(a:Color,b:Color):Float = (maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)
}
