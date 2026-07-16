package com.secondspine.app.export

import com.secondspine.app.data.CaughtEventRow
import com.secondspine.app.data.ConfessionRow
import com.secondspine.app.data.DayRow
import com.secondspine.app.data.HabitRow
import com.secondspine.app.data.LedgerEntryRow
import com.secondspine.app.data.ProofRow
import com.secondspine.app.data.StageTransitionRow
import com.secondspine.app.data.WeightEntryRow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * THE MANIFEST — his own data, in formats that outlive this app.
 *
 * JSON *and* CSV, which is not indecision. They are for two different futures: the JSON is complete
 * and machine-readable for whatever he writes in five years, and the CSVs open in the spreadsheet he
 * already has, today, with no tooling and no goodwill from us. An "open format" that in practice
 * requires a parser somebody has to write first is a promise with a chore attached.
 *
 * Every timestamp is written twice: the raw epoch millis (lossless, sortable, timezone-free) and an
 * ISO-8601 local string (readable by a human at a glance). The archive has to be legible to him
 * without this app, and a column of thirteen-digit integers is not legible to anyone.
 *
 * WHAT IS NOT IN HERE, and never will be:
 *  - **No food, anywhere.** There is no column to export because there is no column. THE DONUT IS
 *    ALLOWED. The absence is in the schema precisely so it cannot be reintroduced by a serializer.
 *  - **No `is_healthy`, no calories, no BMI, no goal weight, no verdict of any kind.** The exporter
 *    copies columns; it does not derive judgements. If a number is not in the database, this file
 *    cannot invent it, and it must not become the place where scoring sneaks in through the back.
 *  - **The isolate is absent.** Its DAO has no SELECT, by design, so there is nothing here to write
 *    even if this file wanted to. Nothing comes out of that table. Not to a report, not to a chart,
 *    and not to a folder on his server.
 *  - **No confession count.** The confessions themselves are exported — they are his words and his
 *    record — but nothing here totals them, because the total is the thing that would turn a free
 *    button into a priced one.
 */
internal data class ArchiveSnapshot(
    val exportedAt: Long,
    val appVersion: String,
    val habits: List<HabitRow>,
    val days: List<DayRow>,
    val transitions: List<StageTransitionRow>,
    val proofs: List<ProofRow>,
    val caught: List<CaughtEventRow>,
    val confessions: List<ConfessionRow>,
    /**
     * ALREADY PURGED — the surviving 28-day window and nothing older.
     *
     * This is the single most important line in the file. The Ledger forgets at 28 days,
     * unconditionally, and the export overwrites `manifest.json` on every run rather than dropping a
     * dated snapshot beside the last one. Those two facts together are what make the forgetting real:
     * if the exporter wrote `manifest-2026-03-14.json` every Sunday, then in ten months his server
     * would hold forty perfectly-preserved copies of every failure the app swore it had deleted, and
     * the 28-day purge would be a local ritual performed over a permanent record. The rumination
     * infrastructure would have been rebuilt by the privacy feature. So: one file, overwritten, and
     * the export forgets exactly when the app forgets.
     */
    val ledger: List<LedgerEntryRow>,
    val weights: List<WeightEntryRow>,
    /** The number the project pre-committed to die on. His instrument, so it leaves with his data. */
    val unpromptedOpensPerDay: Double,
    /** proofId -> the photo's path inside the export, so a row can be matched to a file by hand. */
    val photoPaths: Map<Long, String>,
) {

    // ── JSON ────────────────────────────────────────────────────────────────

    fun toJson(): String = JSONObject().apply {
        put("format", "second-spine-archive")
        put("formatVersion", FORMAT_VERSION)
        put("appVersion", appVersion)
        put("exportedAt", exportedAt)
        put("exportedAtLocal", iso(exportedAt))
        put("timezone", zone.id)
        put(
            "note",
            "Your data. Photos are under proofs/. The ledger section holds the last 28 days only, " +
                "because that is all the app itself keeps. This file is overwritten on every export.",
        )

        put("habits", habits.jsonArray { h ->
            put("id", h.id)
            put("pillar", h.pillar.name)
            put("title", h.title)
            put("stage", h.stage.name)
            put("tier", h.tier.name)
            put("stageSince", h.stageSince)
            put("stageSinceLocal", iso(h.stageSince))
            put("lockEligible", h.lockEligible)
            put("enabled", h.enabled)
        })

        put("days", days.jsonArray { d ->
            put("habitId", d.habitId)
            put("epochDay", d.epochDay)
            put("date", localDate(d.epochDay))
            put("completed", d.completed)
            put("confessed", d.confessed)
            put("suspended", d.suspended)
        })

        put("stageTransitions", transitions.jsonArray { t ->
            put("habitId", t.habitId)
            put("from", t.from.name)
            put("to", t.to.name)
            put("reason", t.reason.name)
            put("at", t.at)
            put("atLocal", iso(t.at))
        })

        put("proofs", proofs.jsonArray { p ->
            put("id", p.id)
            put("habitId", p.habitId)
            put("challengeId", p.challengeId ?: JSONObject.NULL)
            put("pixelSha256", p.pixelSha256)
            put("capturedAt", p.capturedAtWall)
            put("capturedAtLocal", iso(p.capturedAtWall))
            put("capturedAtElapsed", p.capturedAtElapsed)
            put("kind", p.kind.name)
            put("appealed", p.appealed)
            put("voided", p.voided)
            put("file", photoPaths[p.id] ?: JSONObject.NULL)
        })

        put("caughtEvents", caught.jsonArray { c ->
            put("habitId", c.habitId)
            put("proofId", c.proofId)
            put("kind", c.kind.name)
            put("at", c.at)
            put("atLocal", iso(c.at))
        })

        put("confessions", confessions.jsonArray { c ->
            put("habitId", c.habitId)
            put("kind", c.kind.name)
            put("at", c.at)
            put("atLocal", iso(c.at))
            put("note", c.note ?: JSONObject.NULL)
        })

        put("ledgerWindowDays", LEDGER_WINDOW_DAYS)
        put("ledger", ledger.jsonArray { l ->
            put("kind", l.kind.name)
            put("habitId", l.habitId ?: JSONObject.NULL)
            put("at", l.at)
            put("atLocal", iso(l.at))
            put("note", l.note ?: JSONObject.NULL)
        })

        put("weight", weights.jsonArray { w ->
            put("at", w.at)
            put("atLocal", iso(w.at))
            put("kg", w.kg)
        })

        put("instruments", JSONObject().apply {
            put("unpromptedOpensPerDay", unpromptedOpensPerDay)
            put("window", "28 days rolling")
        })
    }.toString(2)

    // ── CSV ─────────────────────────────────────────────────────────────────

    /** Filename -> contents. One table per file, because that is what a spreadsheet expects. */
    fun csvFiles(): Map<String, String> = mapOf(
        "habits.csv" to csv(
            listOf("id", "pillar", "title", "stage", "tier", "stage_since", "stage_since_local", "lock_eligible", "enabled"),
            habits.map { listOf(it.id, it.pillar.name, it.title, it.stage.name, it.tier.name, it.stageSince, iso(it.stageSince), it.lockEligible, it.enabled) },
        ),
        "days.csv" to csv(
            listOf("habit_id", "epoch_day", "date", "completed", "confessed", "suspended"),
            days.map { listOf(it.habitId, it.epochDay, localDate(it.epochDay), it.completed, it.confessed, it.suspended) },
        ),
        "stage_transitions.csv" to csv(
            listOf("habit_id", "from", "to", "reason", "at", "at_local"),
            transitions.map { listOf(it.habitId, it.from.name, it.to.name, it.reason.name, it.at, iso(it.at)) },
        ),
        "proofs.csv" to csv(
            listOf("id", "habit_id", "challenge_id", "pixel_sha256", "captured_at", "captured_at_local", "kind", "appealed", "voided", "file"),
            proofs.map {
                listOf(it.id, it.habitId, it.challengeId ?: "", it.pixelSha256, it.capturedAtWall, iso(it.capturedAtWall), it.kind.name, it.appealed, it.voided, photoPaths[it.id] ?: "")
            },
        ),
        "caught_events.csv" to csv(
            listOf("habit_id", "proof_id", "kind", "at", "at_local"),
            caught.map { listOf(it.habitId, it.proofId, it.kind.name, it.at, iso(it.at)) },
        ),
        "confessions.csv" to csv(
            listOf("habit_id", "kind", "at", "at_local", "note"),
            confessions.map { listOf(it.habitId, it.kind.name, it.at, iso(it.at), it.note ?: "") },
        ),
        "ledger.csv" to csv(
            listOf("kind", "habit_id", "at", "at_local", "note"),
            ledger.map { listOf(it.kind.name, it.habitId ?: "", it.at, iso(it.at), it.note ?: "") },
        ),
        "weight.csv" to csv(
            listOf("at", "at_local", "kg"),
            weights.map { listOf(it.at, iso(it.at), it.kg) },
        ),
    )

    /**
     * RFC 4180. Quote everything that could contain a delimiter and double any embedded quote.
     *
     * `confession.note` and `ledger_entry.note` are free text he typed at 01:00, so they will
     * eventually contain a comma, a quote and a newline, and an export that corrupts his own words is
     * worse than no export.
     */
    private fun csv(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(","))
        rows.forEach { row -> appendLine(row.joinToString(",") { cell(it) }) }
    }

    private fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        if (s.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return s
        return '"' + s.replace("\"", "\"\"") + '"'
    }

    // ── time ────────────────────────────────────────────────────────────────

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun iso(millis: Long): String =
        runCatching {
            Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }.getOrDefault("")

    private fun localDate(epochDay: Long): String =
        runCatching { java.time.LocalDate.ofEpochDay(epochDay).toString() }.getOrDefault("")

    private inline fun <T> List<T>.jsonArray(build: JSONObject.(T) -> Unit): JSONArray =
        JSONArray().also { arr -> forEach { item -> arr.put(JSONObject().apply { build(item) }) } }

    companion object {
        const val FORMAT_VERSION = 1
        const val LEDGER_WINDOW_DAYS = 28
    }
}

/**
 * `schema.md`, shipped beside the data.
 *
 * SPEC §8.6 asks for it and the reason is not documentation-for-its-own-sake: an archive he cannot
 * read in five years without this app installed is not an archive, it is a backup of a hostage. The
 * absences are documented as loudly as the columns, because the absences are the product.
 */
internal val SCHEMA_MD: String = """
# SECOND SPINE — your archive

This folder is yours. Nothing in here needs the app to read it, and nothing in here phones home.

## What's in it

| Path | What it is |
|---|---|
| `proofs/yyyy/MM/*.jpg` | Every photo you banked, in the month you took it. Named `p<proofId>-<original>`. |
| `manifest.json` | Everything below, in one machine-readable file. Overwritten each export. |
| `*.csv` | The same tables, one per file. Opens in any spreadsheet. |
| `schema.md` | This file. |

## The tables

**habits.csv** — one row per habit. `stage` is the pipeline position: ENFORCED -> AUDITED -> TRUSTED
-> RETIRED. Habits climb; the coach loses ground and gets no vote. `stage_since` is when it last
moved.

**days.csv** — one row per scheduled demand per day. `completed` is what happened. `confessed` and
`suspended` are the two columns that take a day out of the compliance ratio entirely — a confessed
day is recorded here honestly as not-completed, and the promotion gate cannot see it.

**stage_transitions.csv** — every move, with the reason. `reason` is one of GRADUATED,
DEMOTED_CAUGHT, DEMOTED_COLLAPSE, SUBDIVIDED, USER_RETIRED. **There is no CONFESSED value.** A
confession is not able to demote you; there is no way to write down that it did.

**proofs.csv** — the archive index. `pixel_sha256` is a hash of the decoded image. `kind` is LIVE or
CONFESSED. There is no `accepted` column: nothing was ever rejected. `file` points at the photo in
this folder.

**caught_events.csv** — usually empty, and it should be. The only `kind` is BYTE_REPLAY: two captures
with byte-identical pixels, which a real camera sensor cannot produce. It is arithmetic, not an
accusation.

**confessions.csv** — what you told it, in your words. Free, unlimited, never counted, never priced.
Purged at 28 days, like everything else about a hard week.

**ledger.csv** — **the last 28 days only.** The app hard-deletes these rows at 28 days,
unconditionally, with no exception for anything repeated. This file is overwritten on every export,
so it forgets exactly when the app forgets. That is on purpose: a permanent, perfectly-preserved
record of your failures is not a memory, it is rumination infrastructure, and this export refuses to
be the back door that rebuilds it.

**weight.csv** — the raw numbers you entered, and only those. There is no goal weight, no BMI, no
trend judgement, no colour and no penalty anywhere in this app, so there is none here.

## What is deliberately absent

There is **no food data**. Not a calorie, not a macro, not an `is_healthy` flag, not a photo verdict.
There is no column for it in the database, so there is nothing here to export and no way to add it
later without changing the schema in daylight. The donut is allowed.

There is **no record of the break-glass button** in this export, in any report, or anywhere else.
Nothing reads that table. Ever.

There are **no confidence scores, model names, or inference internals** — none of that was ever
stored.

## Timestamps

Everything is written twice: `*_at` is epoch milliseconds (UTC, lossless), `*_at_local` is ISO-8601
in the timezone the export ran in. Trust the epoch column if they ever disagree.
""".trimIndent()
