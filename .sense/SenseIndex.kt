package com.stremio.mobile.sense

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class SenseItem(val id: String, val name: String, val type: String, val vector: ByteArray)
data class SenseHit(val id: String, val name: String, val type: String, val score: Double)

class SenseIndex private constructor(val dimensions: Int, val items: List<SenseItem>) {
    private val positions = items.mapIndexed { index, item -> item.id to index }.toMap()
    fun item(id: String): SenseItem? = positions[id]?.let(items::get)
    fun similar(queryId: String, limit: Int = 20, exclude: Set<String> = emptySet(), type: String? = null): List<SenseHit> {
        if (limit <= 0) return emptyList()
        val query = item(queryId) ?: return emptyList()
        return items.asSequence().filter { it.id != queryId && it.id !in exclude && (type == null || it.type == type) }
            .map { SenseHit(it.id, it.name, it.type, cosine(query.vector, it.vector)) }
            .sortedWith(compareByDescending<SenseHit> { it.score }.thenBy { it.name }).take(limit).toList()
    }
    fun similarDiverse(queryId: String, candidateLimit: Int = 80, resultLimit: Int = 20, relevanceWeight: Double = 0.72, exclude: Set<String> = emptySet(), type: String? = null): List<SenseHit> =
        diversify(similar(queryId, max(candidateLimit, resultLimit), exclude, type), resultLimit, relevanceWeight)
    fun recommendFromHistory(historyIds: List<String>, resultLimit: Int = 20, candidateLimit: Int = max(160, resultLimit * 10), seedLimit: Int = 12, relevanceWeight: Double = 0.76, temperature: Double = 0.12, type: String? = null): List<SenseHit> {
        val seen = historyIds.toHashSet()
        val seeds = historyIds.takeLast(seedLimit).mapNotNull(::item).asReversed()
        if (seeds.isEmpty()) return emptyList()
        val candidates = items.asSequence().filter { it.id !in seen && (type == null || it.type == type) }.map { candidate ->
            val similarities = seeds.mapIndexed { index, seed -> cosine(seed.vector, candidate.vector) + 0.035 * exp(-index / 5.0) }
            SenseHit(candidate.id, candidate.name, candidate.type, softmaxPool(similarities, temperature))
        }.sortedByDescending { it.score }.take(candidateLimit).toList()
        return diversify(candidates, resultLimit, relevanceWeight)
    }
    private fun diversify(candidates: List<SenseHit>, resultLimit: Int, relevanceWeight: Double): List<SenseHit> {
        val lambda = relevanceWeight.coerceIn(0.0, 1.0); val remaining = candidates.toMutableList(); val selected = mutableListOf<SenseHit>()
        while (remaining.isNotEmpty() && selected.size < resultLimit) {
            var bestIndex = 0; var bestScore = Double.NEGATIVE_INFINITY
            remaining.forEachIndexed { index, candidate ->
                val candidateVector = item(candidate.id)?.vector ?: return@forEachIndexed
                var redundancy = 0.0
                selected.forEach { picked -> val pickedVector = item(picked.id)?.vector ?: return@forEach; redundancy = max(redundancy, max(0.0, cosine(candidateVector, pickedVector))) }
                val mmr = lambda * candidate.score - (1.0 - lambda) * redundancy
                if (mmr > bestScore) { bestScore = mmr; bestIndex = index }
            }
            selected += remaining.removeAt(bestIndex)
        }
        return selected
    }
    companion object {
        private const val MAGIC = "SENSEIDX"; private const val VERSION = 2
        fun fromBytes(raw: ByteArray): SenseIndex {
            require(raw.size >= 16) { "truncated Sense index" }; val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); val magicRaw = ByteArray(8); buffer.get(magicRaw)
            require(magicRaw.toString(Charsets.US_ASCII) == MAGIC) { "invalid Sense index magic" }; val version = buffer.short.toInt() and 0xffff; require(version == VERSION) { "unsupported Sense index version $version" }
            val dimensions = buffer.short.toInt() and 0xffff; require(dimensions > 0); val count = buffer.int; require(count >= 0)
            val items = ArrayList<SenseItem>(count); val seen = HashSet<String>(count * 2)
            repeat(count) { val id = readString(buffer); require(seen.add(id)); val type = when (buffer.get().toInt() and 0xff) { 1 -> "movie"; 2 -> "series"; else -> "other" }; val name = readString(buffer); require(buffer.remaining() >= dimensions); val vector = ByteArray(dimensions); buffer.get(vector); items += SenseItem(id, name, type, vector) }
            require(!buffer.hasRemaining()); return SenseIndex(dimensions, items)
        }
        private fun readString(buffer: ByteBuffer): String { require(buffer.remaining() >= 2); val length = buffer.short.toInt() and 0xffff; require(buffer.remaining() >= length); val bytes = ByteArray(length); buffer.get(bytes); return bytes.toString(Charsets.UTF_8) }
        private fun cosine(a: ByteArray, b: ByteArray): Double { if (a.isEmpty() || a.size != b.size) return 0.0; var dot=0.0; var a2=0.0; var b2=0.0; for (i in a.indices) { val x=a[i].toInt().toDouble(); val y=b[i].toInt().toDouble(); dot+=x*y; a2+=x*x; b2+=y*y }; return if (a2==0.0 || b2==0.0) 0.0 else dot/sqrt(a2*b2) }
        private fun softmaxPool(values: List<Double>, temperature: Double): Double { if (values.isEmpty()) return Double.NEGATIVE_INFINITY; val t=max(0.01,temperature); val maxValue=values.max(); val sum=values.sumOf { exp((it-maxValue)/t) }; return maxValue+t*ln(sum/values.size) }
    }
}
