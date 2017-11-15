package org.kaqui.model

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.kaqui.data.Hiraganas
import org.kaqui.data.SimilarHiraganas

class KaquiDb private constructor(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${KANJIS_TABLE_NAME} ("
                        + "id_kanji INTEGER PRIMARY KEY,"
                        + "kanji TEXT NOT NULL UNIQUE,"
                        + "jlpt_level INTEGER NOT NULL,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${READINGS_TABLE_NAME} ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "reading_type TEXT NOT NULL,"
                        + "reading TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${MEANINGS_TABLE_NAME} ("
                        + "id_reading INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "meaning TEXT NOT NULL"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILARITIES_TABLE_NAME} ("
                        + "id_similarity INTEGER PRIMARY KEY,"
                        + "id_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji),"
                        + "similar_kanji INTEGER NOT NULL REFERENCES kanjis(id_kanji)"
                        + ")")

        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${HIRAGANAS_TABLE_NAME} ("
                        + "id_hiragana INTEGER PRIMARY KEY,"
                        + "hiragana TEXT NOT NULL UNIQUE,"
                        + "romaji TEXT NOT NULL,"
                        + "short_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "long_score FLOAT NOT NULL DEFAULT 0.0,"
                        + "last_correct INTEGER NOT NULL DEFAULT 0,"
                        + "enabled INTEGER NOT NULL DEFAULT 1"
                        + ")")
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS ${SIMILAR_HIRAGANAS_TABLE_NAME} ("
                        + "id_similar_hiragana INTEGER PRIMARY KEY,"
                        + "id_hiragana INTEGER NOT NULL REFERENCES ${HIRAGANAS_TABLE_NAME}(id_hiragana),"
                        + "similar_hiragana INTEGER NOT NULL REFERENCES ${HIRAGANAS_TABLE_NAME}(id_hiragana),"
                        + "UNIQUE (id_hiragana, similar_hiragana)"
                        + ")")

        val hiraganaCount = database.query(HIRAGANAS_TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (hiraganaCount == 0) {
            for (hiragana in Hiraganas) {
                val cv = ContentValues()
                cv.put("hiragana", hiragana.kana)
                cv.put("romaji", hiragana.romaji)
                database.insertOrThrow(HIRAGANAS_TABLE_NAME, null, cv)
            }
        }
        val similarHiraganaCount = database.query(SIMILAR_HIRAGANAS_TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null).use {
            it.moveToFirst()
            it.getInt(0)
        }
        if (similarHiraganaCount == 0) {
            for (similarHiragana in SimilarHiraganas) {
                val id1 = database.query(HIRAGANAS_TABLE_NAME, arrayOf("id_hiragana"), "romaji = ?", arrayOf(similarHiragana.kana), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
                val id2 = database.query(HIRAGANAS_TABLE_NAME, arrayOf("id_hiragana"), "romaji = ?", arrayOf(similarHiragana.similar), null, null, null).use {
                    it.moveToFirst()
                    it.getInt(0)
                }

                val cv = ContentValues()
                cv.put("id_hiragana", id1)
                cv.put("similar_hiragana", id2)
                database.insertOrThrow(SIMILAR_HIRAGANAS_TABLE_NAME, null, cv)
                cv.put("id_hiragana", id2)
                cv.put("similar_hiragana", id1)
                database.insertOrThrow(SIMILAR_HIRAGANAS_TABLE_NAME, null, cv)
            }
        }
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            database.execSQL("DROP TABLE IF EXISTS ${SIMILARITIES_TABLE_NAME}")
            onCreate(database)
            return
        }
        if (oldVersion < 4) {
            database.execSQL("DROP TABLE IF EXISTS tmptable")
            database.execSQL("ALTER TABLE ${KANJIS_TABLE_NAME} RENAME TO tmptable")
            onCreate(database)
            database.execSQL(
                    "INSERT INTO ${KANJIS_TABLE_NAME} (id_kanji, kanji, jlpt_level, short_score, enabled) "
                            + "SELECT id_kanji, kanji, jlpt_level, weight, enabled FROM tmptable")
            database.execSQL("DROP TABLE tmptable")
            return
        }
        if (oldVersion < 5) {
            onCreate(database)
        }
        if (oldVersion < 6) {
            onCreate(database)
        }
    }

    val empty: Boolean
        get() = DatabaseUtils.queryNumEntries(readableDatabase, KANJIS_TABLE_NAME) == 0L

    fun replaceKanjis(kanjis: List<Kanji>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(MEANINGS_TABLE_NAME, null, null)
            writableDatabase.delete(READINGS_TABLE_NAME, null, null)
            writableDatabase.delete(SIMILARITIES_TABLE_NAME, null, null)
            writableDatabase.delete(KANJIS_TABLE_NAME, null, null)
            for (kanji in kanjis) {
                val kanjiCv = ContentValues()
                kanjiCv.put("kanji", kanji.kanji)
                kanjiCv.put("jlpt_level", kanji.jlptLevel)
                val kanjiId = writableDatabase.insertOrThrow(KANJIS_TABLE_NAME, null, kanjiCv)
                for (reading in kanji.readings) {
                    val readingCv = ContentValues()
                    readingCv.put("id_kanji", kanjiId)
                    readingCv.put("reading_type", reading.readingType)
                    readingCv.put("reading", reading.reading)
                    writableDatabase.insertOrThrow(READINGS_TABLE_NAME, null, readingCv)
                }
                for (meaning in kanji.meanings) {
                    val meaningCv = ContentValues()
                    meaningCv.put("id_kanji", kanjiId)
                    meaningCv.put("meaning", meaning)
                    writableDatabase.insertOrThrow(MEANINGS_TABLE_NAME, null, meaningCv)
                }
            }

            for (kanji in kanjis) {
                for (similarity in kanji.similarities) {
                    val kanjiId = getKanjiId(kanji.kanji[0])!!
                    val similarityId = getKanjiId((similarity.contents as Kanji).kanji[0])
                    similarityId ?: continue

                    val similarityCv = ContentValues()
                    similarityCv.put("id_kanji", kanjiId)
                    similarityCv.put("similar_kanji", similarityId)
                    writableDatabase.insertOrThrow(SIMILARITIES_TABLE_NAME, null, similarityCv)
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun getKanjiId(kanji: Char): Int? {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("id_kanji"), "kanji = ?", arrayOf(kanji.toString()), null, null, null).use { cursor ->
            return if (cursor.count == 0)
                null
            else {
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }

    val kanjiView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, KANJIS_TABLE_NAME, "id_kanji", this::getKanji)
    val hiraganaView: LearningDbView
        get() = LearningDbView(readableDatabase, writableDatabase, HIRAGANAS_TABLE_NAME, "id_hiragana", this::getHiragana)

    fun search(text: String): List<Int> {
        readableDatabase.rawQuery(
                """SELECT DISTINCT k.id_kanji
                FROM kanjis k
                LEFT JOIN readings r USING(id_kanji)
                LEFT JOIN meanings m USING(id_kanji)
                WHERE k.kanji = ? OR r.reading LIKE ? OR m.meaning LIKE ?""",
                arrayOf(text, "%$text%", "%$text%")).use { cursor ->
            val ret = mutableListOf<Int>()
            while (cursor.moveToNext()) {
                ret.add(cursor.getInt(0))
            }
            return ret
        }
    }

    fun getHiragana(id: Int): Item {
        val similarities = mutableListOf<Item>()
        readableDatabase.query(SIMILAR_HIRAGANAS_TABLE_NAME, arrayOf("similar_hiragana"), "id_hiragana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kana("", "", listOf()), 0.0, 0.0, 0, false))
        }
        val contents = Kana("", "", similarities)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(HIRAGANAS_TABLE_NAME, arrayOf("hiragana", "romaji", "short_score", "long_score", "last_correct", "enabled"), "id_hiragana = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find hiragana with id " + id)
            cursor.moveToFirst()
            contents.kana = cursor.getString(0)
            contents.romaji = cursor.getString(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
        }
        return item
    }

    fun getKanji(id: Int): Item {
        val readings = mutableListOf<Reading>()
        val meanings = mutableListOf<String>()
        val similarities = mutableListOf<Item>()
        readableDatabase.query(READINGS_TABLE_NAME, arrayOf("reading_type", "reading"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                readings.add(Reading(cursor.getString(0), cursor.getString(1)))
        }
        readableDatabase.query(MEANINGS_TABLE_NAME, arrayOf("meaning"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                meanings.add(cursor.getString(0))
        }
        readableDatabase.query(SIMILARITIES_TABLE_NAME, arrayOf("similar_kanji"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext())
                similarities.add(Item(cursor.getInt(0), Kanji("", listOf(), listOf(), listOf(), 0), 0.0, 0.0, 0, false))
        }
        val contents = Kanji("", readings, meanings, similarities, 0)
        val item = Item(id, contents, 0.0, 0.0, 0, false)
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "jlpt_level", "short_score", "long_score", "last_correct", "enabled"), "id_kanji = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
            if (cursor.count == 0)
                throw RuntimeException("Can't find kanji with id " + id)
            cursor.moveToFirst()
            contents.kanji = cursor.getString(0)
            contents.jlptLevel = cursor.getInt(1)
            item.shortScore = cursor.getDouble(2)
            item.longScore = cursor.getDouble(3)
            item.lastCorrect = cursor.getLong(4)
            item.enabled = cursor.getInt(5) != 0
        }
        return item
    }

    fun setSelection(kanjis: String) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            cv.put("enabled", false)
            writableDatabase.update(KANJIS_TABLE_NAME, cv, null, null)
            cv.put("enabled", true)
            for (c in kanjis) {
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(c.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    data class DumpRow(val shortScore: Float, val longScore: Float, val lastCorrect: Long, val enabled: Boolean)

    fun dumpUserData(): Map<Char, DumpRow> {
        readableDatabase.query(KANJIS_TABLE_NAME, arrayOf("kanji", "short_score", "long_score", "last_correct", "enabled"), null, null, null, null, null).use { cursor ->
            val ret = mutableMapOf<Char, DumpRow>()
            while (cursor.moveToNext()) {
                ret[cursor.getString(0)[0]] = DumpRow(cursor.getFloat(1), cursor.getFloat(2), cursor.getLong(3), cursor.getInt(4) != 0)
            }
            return ret
        }
    }

    fun restoreUserDataDump(data: Map<Char, DumpRow>) {
        writableDatabase.beginTransaction()
        try {
            val cv = ContentValues()
            for ((kanji, row) in data) {
                cv.put("short_score", row.shortScore)
                cv.put("long_score", row.longScore)
                cv.put("last_correct", row.lastCorrect)
                cv.put("enabled", if (row.enabled) 1 else 0)
                writableDatabase.update(KANJIS_TABLE_NAME, cv, "kanji = ?", arrayOf(kanji.toString()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    companion object {
        private const val TAG = "KaquiDb"

        private const val DATABASE_NAME = "kanjis"
        private const val DATABASE_VERSION = 6

        private const val HIRAGANAS_TABLE_NAME = "hiraganas"
        private const val SIMILAR_HIRAGANAS_TABLE_NAME = "similar_hiraganas"

        private const val KANJIS_TABLE_NAME = "kanjis"
        private const val READINGS_TABLE_NAME = "readings"
        private const val MEANINGS_TABLE_NAME = "meanings"
        private const val SIMILARITIES_TABLE_NAME = "similarities"

        private var singleton: KaquiDb? = null

        fun getInstance(context: Context): KaquiDb {
            if (singleton == null)
                singleton = KaquiDb(context)
            return singleton!!
        }
    }
}