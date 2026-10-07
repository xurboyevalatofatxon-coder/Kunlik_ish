package uz.dailygoals.domain

import java.time.LocalDate

/** Strict dependency-free JSON codec; no reflection, eval, network, or platform APIs.
 * Duplicate keys, malformed escapes, trailing bytes, excessive nesting and size are rejected.
 * Only data are exported: PIN credentials and notification/settings preferences are excluded.
 */
object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private fun date(d: Long) = LocalDate.ofEpochDay(d).toString()
    private fun obj(vararg pairs: Pair<String, Any?>) = linkedMapOf(*pairs)
    fun encode(s: Snapshot): String = Json.write(obj(
        "schemaVersion" to s.schemaVersion, "exportedAt" to s.exportedAt,
        "timezone" to "Asia/Tashkent", "endDateInclusive" to true,
        "goals" to s.goals.map { obj("id" to it.id, "rootId" to it.rootId, "insertionOrder" to it.insertionOrder, "createdAt" to it.createdAt, "status" to it.status.name) },
        "periods" to s.periods.map { obj("id" to it.id, "goalId" to it.goalId, "name" to it.name,
            "startDate" to date(it.start), "trackingStart" to date(it.trackingStart), "endDate" to date(it.end),
            "trackingEnd" to date(it.trackingEnd), "status" to it.status.name, "periodOrder" to it.periodOrder,
            "createdAt" to it.createdAt, "archivedAt" to it.archivedAt, "archiveReason" to it.archiveReason?.name) },
        "nameRevisions" to s.revisions.map { obj("id" to it.id, "periodId" to it.periodId, "from" to date(it.from), "name" to it.name) },
        "dailyResults" to s.results.map { obj("id" to it.id, "periodId" to it.periodId, "date" to date(it.date),
            "result" to it.value.name, "goalNameSnapshot" to it.goalNameSnapshot, "recordedAt" to it.recordedAt, "finalized" to it.finalized) },
        "finalizedDays" to s.days.map { obj("date" to date(it.date), "finalizedAt" to it.finalizedAt) }
    ))
    fun decode(text: String, today: Long): Snapshot {
        ensure(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, ErrorCode.FILE_TOO_LARGE)
        try {
            val root = Json.parse(text).objectValue()
            val rawVersion = root.long("schemaVersion")
            ensure(rawVersion == 1L, ErrorCode.UNSUPPORTED_SCHEMA)
            val version = rawVersion.toInt()
            ensure(root.str("timezone") == "Asia/Tashkent" && root.bool("endDateInclusive"), ErrorCode.INVALID_BACKUP)
            val result = Snapshot(version, root.long("exportedAt"),
                root.array("goals").map { m -> Goal(m.str("id"), m.str("rootId"), m.long("insertionOrder"), m.long("createdAt"), enumValueOf(m.str("status"))) },
                root.array("periods").map { m -> GoalPeriod(m.str("id"), m.str("goalId"), m.str("name"),
                    m.date("startDate"), m.date("trackingStart"), m.date("endDate"), enumValueOf(m.str("status")),
                    m.long("periodOrder").also { require(it in 1..Int.MAX_VALUE.toLong()) }.toInt(), m.long("createdAt"), m.optionalLong("archivedAt"),
                    m.optionalString("archiveReason")?.let { enumValueOf<ArchiveReason>(it) }, m.date("trackingEnd")) },
                root.array("nameRevisions").map { m -> NameRevision(m.str("id"), m.str("periodId"), m.date("from"), m.str("name")) },
                root.array("dailyResults").map { m -> DailyResult(m.str("id"), m.str("periodId"), m.date("date"),
                    enumValueOf(m.str("result")), m.str("goalNameSnapshot"), m.long("recordedAt"), m.bool("finalized")) },
                root.array("finalizedDays").map { m -> FinalizedDay(m.date("date"), m.long("finalizedAt")) }
            )
            BackupValidator.validate(result, today)
            return result
        } catch (e: DomainException) { throw e }
        catch (_: Exception) { throw DomainException(ErrorCode.INVALID_BACKUP) }
    }
    @Suppress("UNCHECKED_CAST") private fun Any?.objectValue() = this as? Map<String, Any?> ?: error("object")
    private fun Map<String, Any?>.str(k: String) = this[k] as? String ?: error(k)
    private fun Map<String, Any?>.long(k: String) = this[k] as? Long ?: error(k)
    private fun Map<String, Any?>.bool(k: String) = this[k] as? Boolean ?: error(k)
    private fun Map<String, Any?>.optionalLong(k: String): Long? { require(containsKey(k)); return this[k]?.let { it as? Long ?: error(k) } }
    private fun Map<String, Any?>.optionalString(k: String): String? { require(containsKey(k)); return this[k]?.let { it as? String ?: error(k) } }
    private fun Map<String, Any?>.date(k: String): Long { val v = str(k); val d = LocalDate.parse(v); require(d.toString() == v); return d.toEpochDay() }
    private fun Map<String, Any?>.array(k: String) = (this[k] as? List<*> ?: error(k)).map { it.objectValue() }
}

internal object Json {
    fun write(value: Any?): String = buildString { appendValue(value) }
    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> {
                append('"')
                value.forEach { c -> when(c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                    else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
                } }
                append('"')
            }
            is Boolean, is Int, is Long -> append(value)
            is Map<*, *> -> { append('{'); value.entries.forEachIndexed { i, e -> if(i > 0) append(','); appendValue(e.key); append(':'); appendValue(e.value) }; append('}') }
            is List<*> -> { append('['); value.forEachIndexed { i, e -> if (i > 0) append(','); appendValue(e) }; append(']') }
            else -> error("unsupported value")
        }
    }
    fun parse(input: String): Any? = Parser(input).parse()
    private class Parser(val s: String) {
        var i = 0
        fun parse(): Any? { val v = value(0); ws(); require(i == s.length); return v }
        fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }
        fun take(c: Char): Boolean { ws(); return if (i < s.length && s[i] == c) { i++; true } else false }
        fun value(depth: Int): Any? {
            require(depth < 32); ws(); require(i < s.length)
            return when(s[i]) {
                '{' -> { i++; val m = linkedMapOf<String, Any?>(); if(!take('}')) {
                    do { ws(); val k = string(); require(!m.containsKey(k)); require(take(':')); m[k] = value(depth + 1) } while(take(','))
                    require(take('}'))
                }; m }
                '[' -> { i++; val a = mutableListOf<Any?>(); if(!take(']')) {
                    do { a += value(depth + 1); require(a.size <= 500_000) } while(take(',')); require(take(']'))
                }; a }
                '"' -> string()
                't' -> { literal("true"); true }
                'f' -> { literal("false"); false }
                'n' -> { literal("null"); null }
                else -> number()
            }
        }
        fun literal(v: String) { require(s.startsWith(v, i)); i += v.length }
        fun number(): Long {
            val start = i
            if(i < s.length && s[i] == '-') i++
            require(i < s.length && s[i] in '0'..'9')
            if(s[i] == '0') i++ else while(i < s.length && s[i] in '0'..'9') i++
            return s.substring(start, i).toLong()
        }
        fun string(): String {
            require(i < s.length && s[i++] == '"'); val out = StringBuilder()
            while(i < s.length) {
                val c = s[i++]
                if(c == '"') return out.toString()
                require(c.code >= 32)
                if(c != '\\') out.append(c) else {
                    require(i < s.length)
                    when(val e = s[i++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000c'); 'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> { require(i + 4 <= s.length); val unit = s.substring(i, i + 4).toInt(16).toChar(); i += 4
                            if (unit.isHighSurrogate()) {
                                require(i + 6 <= s.length && s.substring(i,i+2) == "\\u"); i += 2
                                val low = s.substring(i,i+4).toInt(16).toChar(); i += 4; require(low.isLowSurrogate()); out.append(unit).append(low)
                            } else { require(!unit.isLowSurrogate()); out.append(unit) }
                        }
                        else -> error("escape")
                    }
                }
            }; error("unterminated string")
        }
    }
}
