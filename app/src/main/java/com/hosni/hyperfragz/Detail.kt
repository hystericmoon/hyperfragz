package com.hosni.hyperfragz

import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * A perfume page, as extracted by assets/extract.js. Every section is nullable:
 * a section that failed to extract (or doesn't exist on the page) is hidden.
 */
data class Detail(
    val url: String,
    val name: String,
    val brand: String,
    val image: String?,
    val family: String?,
    val audience: String?,
    val year: String?,
    val perfumers: String?,
    val rating: Rating?,
    val accords: List<Accord>?,
    val notes: Notes?,
    val longevity: List<Count>?,
    val sillage: List<Count>?,
    val wear: List<Count>?,
    val pros: List<Opinion>?,
    val cons: List<Opinion>?,
    val similar: List<Related>?,
    val alsoLike: List<Related>?,
    val reviews: Reviews?,
    /** False while lazy sections are still loading on the hidden page. */
    val complete: Boolean,
) {
    /** "An oriental vanilla for women, launched in 2014." */
    val familyLine: String?
        get() {
            val fam = family?.lowercase() ?: return year?.let { "Launched in $it." }
            val article = if (fam.first() in "aeiou") "An" else "A"
            val who = when (audience?.lowercase()) {
                "women" -> " for women"
                "men" -> " for men"
                "women and men" -> " for everyone"
                else -> ""
            }
            // "An oriental vanilla …" reads fine; a single word needs a noun: "A woody fragrance …".
            val noun = if (' ' in fam) fam else "$fam fragrance"
            return "$article $noun$who" + (year?.let { ", launched in $it." } ?: ".")
        }

    /** Sections every perfume page normally has, but that came up empty here. */
    fun missingSections(): List<String> = listOfNotNull(
        "rating".takeIf { rating == null },
        "accords".takeIf { accords == null },
        "notes".takeIf { notes == null },
        "on skin".takeIf { longevity == null && sillage == null },
        "best worn".takeIf { wear == null },
        "reviews".takeIf { reviews?.items.isNullOrEmpty() },
    )

    /** This snapshot, with [old]'s values filling in anything this one lacks. */
    fun mergedOver(old: Detail) = copy(
        name = name.ifEmpty { old.name },
        brand = brand.ifEmpty { old.brand },
        image = image ?: old.image,
        family = family ?: old.family,
        audience = audience ?: old.audience,
        year = year ?: old.year,
        perfumers = perfumers ?: old.perfumers,
        rating = if (rating?.breakdown == null && old.rating?.breakdown != null) old.rating else rating ?: old.rating,
        accords = accords ?: old.accords,
        notes = notes ?: old.notes,
        longevity = longevity ?: old.longevity,
        sillage = sillage ?: old.sillage,
        wear = wear ?: old.wear,
        pros = pros ?: old.pros,
        cons = cons ?: old.cons,
        similar = similar ?: old.similar,
        alsoLike = alsoLike ?: old.alsoLike,
        reviews = if (reviews?.items.isNullOrEmpty() && !old.reviews?.items.isNullOrEmpty() && (reviews == null || reviews.sort == old.reviews?.sort)) old.reviews
            else reviews ?: old.reviews,
    )

    companion object {
        /** Instant header while the page loads. */
        fun preview(hit: Hit) = Detail(
            url = hit.url, name = hit.name, brand = hit.brand, image = hit.thumb,
            family = null, audience = null, year = hit.year, perfumers = null, rating = null,
            accords = null, notes = null, longevity = null, sillage = null, wear = null,
            pros = null, cons = null, similar = null, alsoLike = null, reviews = null,
            complete = false,
        )

        fun preview(r: Related) = preview(Hit(r.url, r.name, r.brand, null, null, r.image))

        fun parse(o: JSONObject, complete: Boolean): Detail {
            val id = o.optJSONObject("identity")
            val about = o.optJSONObject("about")
            val pc = o.optJSONObject("prosCons")
            return Detail(
                url = o.optString("url"),
                name = id?.optString("name").orEmpty(),
                brand = id?.optString("brand").orEmpty(),
                image = id?.optStr("image"),
                family = about?.optStr("family"),
                audience = about?.optStr("audience"),
                year = about?.optStr("year"),
                perfumers = o.optStr("perfumers"),
                rating = section { o.optJSONObject("rating")?.let(Rating::parse) },
                accords = section { o.optJSONArray("accords").objects().map { Accord(it.optString("label"), it.optDouble("width", 0.0).toFloat()) }.ifEmpty { null } },
                notes = section { o.optJSONObject("notes")?.let(Notes::parse) },
                longevity = section { counts(o, "longevity") },
                sillage = section { counts(o, "sillage") },
                wear = section { counts(o, "wear") },
                pros = section { pc?.optJSONArray("pros").objects().map(Opinion::parse).ifEmpty { null } },
                cons = section { pc?.optJSONArray("cons").objects().map(Opinion::parse).ifEmpty { null } },
                similar = section { o.optJSONArray("similar").objects().mapNotNull(Related::parse).ifEmpty { null } },
                alsoLike = section { o.optJSONArray("alsoLike").objects().mapNotNull(Related::parse).ifEmpty { null } },
                reviews = section { o.optJSONObject("reviews")?.let(Reviews::parse) },
                complete = complete,
            )
        }

        private fun counts(o: JSONObject, key: String) =
            o.optJSONArray(key).objects().map { Count(it.optString("label"), it.optInt("count")) }
                .takeIf { list -> list.isNotEmpty() && list.sumOf { it.count } > 0 }

        /** One bad section must never take the screen down. */
        private inline fun <T> section(block: () -> T?): T? = runCatching(block).getOrNull()
    }
}

data class Rating(val value: Double, val votes: Int?, val breakdown: List<Count>?) {
    /** Share of love + like, 0..100. */
    val positivePct: Int?
        get() {
            val b = breakdown ?: return null
            val total = b.sumOf { it.count }.takeIf { it > 0 } ?: return null
            val pos = b.filter { it.label.lowercase() in setOf("love", "like") }.sumOf { it.count }
            return (pos * 100f / total).roundToInt()
        }

    companion object {
        fun parse(o: JSONObject) = Rating(
            value = o.optDouble("value"),
            votes = o.optIntOrNull("votes"),
            breakdown = o.optJSONArray("breakdown").objects().map { Count(it.optString("label"), it.optInt("count")) }.ifEmpty { null },
        )
    }
}

data class Accord(val label: String, val width: Float)

data class Notes(val top: List<String>, val middle: List<String>, val base: List<String>, val flat: List<String>) {
    companion object {
        fun parse(o: JSONObject): Notes? {
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            val n = Notes(list("top"), list("middle"), list("base"), list("flat"))
            return n.takeIf { it.top.isNotEmpty() || it.middle.isNotEmpty() || it.base.isNotEmpty() || it.flat.isNotEmpty() }
        }
    }
}

data class Count(val label: String, val count: Int)

data class Opinion(val text: String, val agree: Int, val disagree: Int) {
    val total get() = agree + disagree
    val pct get() = if (total == 0) 0 else (agree * 100f / total).roundToInt()

    companion object {
        fun parse(o: JSONObject) = Opinion(o.optString("text"), o.optInt("agree"), o.optInt("disagree"))
    }
}

/** A carousel card ("reminds people of" has votes, "if you like this" doesn't). */
data class Related(val url: String, val name: String, val brand: String, val image: String?, val yes: Int?, val no: Int?) {
    val total get() = (yes ?: 0) + (no ?: 0)
    val pct get() = if (total == 0) null else ((yes ?: 0) * 100f / total).roundToInt()

    companion object {
        fun parse(o: JSONObject): Related? {
            val url = o.optStr("url") ?: return null
            return Related(url, o.optString("name"), o.optString("brand"), o.optStr("image"), o.optIntOrNull("yes"), o.optIntOrNull("no"))
        }
    }
}

data class Reviews(val total: Int?, val sort: String, val items: List<Review>) {
    companion object {
        fun parse(o: JSONObject) = Reviews(
            total = o.optIntOrNull("total"),
            sort = o.optString("sort", "newest"),
            items = o.optJSONArray("items").objects().map(Review::parse),
        )
    }
}

data class Review(val id: String, val user: String, val date: String, val verdict: String?, val tags: List<String>, val text: String) {
    companion object {
        fun parse(o: JSONObject) = Review(
            id = o.optString("id"),
            user = o.optString("user"),
            date = o.optString("date"),
            verdict = o.optStr("verdict"),
            tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            text = o.optString("text"),
        )
    }
}

/** optString that maps missing/null/blank to null. */
internal fun JSONObject.optStr(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
