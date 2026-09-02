package io.github.jiangyuyi.lightnovel.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.jiangyuyi.lightnovel.core.model.LocalReadingProgress
import io.github.jiangyuyi.lightnovel.core.model.ReaderFont
import io.github.jiangyuyi.lightnovel.core.model.ReaderChineseScript
import io.github.jiangyuyi.lightnovel.core.model.ReaderMode
import io.github.jiangyuyi.lightnovel.core.model.ReaderPreferences
import io.github.jiangyuyi.lightnovel.core.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readerDataStore by preferencesDataStore(name = "reader_preferences")

class ReaderPreferencesStore(private val context: Context) {
    val preferences: Flow<ReaderPreferences> = context.readerDataStore.data.map { values ->
        ReaderPreferences(
            font = enumValueOrDefault(values[FONT], ReaderFont.SERIF),
            fontSize = (values[FONT_SIZE] ?: 19f).coerceIn(14f, 32f),
            lineHeight = (values[LINE_HEIGHT] ?: 1.7f).coerceIn(1.2f, 2.2f),
            horizontalPadding = (values[PADDING] ?: 22).coerceIn(12, 40),
            theme = enumValueOrDefault(values[THEME], ReaderTheme.SEPIA),
            mode = enumValueOrDefault(values[MODE], ReaderMode.PAGED),
            chineseScript = enumValueOrDefault(values[CHINESE_SCRIPT], ReaderChineseScript.ORIGINAL),
        )
    }

    suspend fun update(value: ReaderPreferences) {
        context.readerDataStore.edit { values ->
            values[FONT] = value.font.name
            values[FONT_SIZE] = value.fontSize.coerceIn(14f, 32f)
            values[LINE_HEIGHT] = value.lineHeight.coerceIn(1.2f, 2.2f)
            values[PADDING] = value.horizontalPadding.coerceIn(12, 40)
            values[THEME] = value.theme.name
            values[MODE] = value.mode.name
            values[CHINESE_SCRIPT] = value.chineseScript.name
        }
    }

    fun progress(bookId: Long): Flow<LocalReadingProgress?> = context.readerDataStore.data.map { values ->
        values[stringPreferencesKey("progress_$bookId")]?.split(':')?.let { parts ->
            if (parts.size != 3) return@let null
            val chapterId = parts[0].toLongOrNull() ?: return@let null
            val paragraph = parts[1].toIntOrNull() ?: return@let null
            val percent = parts[2].toIntOrNull() ?: return@let null
            LocalReadingProgress(chapterId, paragraph, percent.coerceIn(0, 100))
        }
    }

    suspend fun saveProgress(bookId: Long, progress: LocalReadingProgress) {
        context.readerDataStore.edit { values ->
            values[stringPreferencesKey("progress_$bookId")] =
                "${progress.chapterId}:${progress.paragraphIndex}:${progress.percent.coerceIn(0, 100)}"
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } } ?: default

    private companion object {
        val FONT = stringPreferencesKey("font")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val PADDING = intPreferencesKey("horizontal_padding")
        val THEME = stringPreferencesKey("theme")
        val MODE = stringPreferencesKey("reader_mode")
        val CHINESE_SCRIPT = stringPreferencesKey("reader_chinese_script")
    }
}
