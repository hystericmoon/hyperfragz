package com.hosni.hyperfragz

import org.json.JSONArray
import org.json.JSONObject

/** Snapshot of the search page, as reported by assets/bridge.js. */
data class PageState(
    val page: String = "loading",
    val url: String = "",
    val total: Int? = null,
    val hits: Int = 0,
    /** Loaded result cards (the site's infinite list, 30 per page). */
    val hitList: List<Hit> = emptyList(),
    val query: String = "",
    val sortOptions: List<SortOption> = emptyList(),
    val sortSelected: String? = null,
    val panels: List<Panel> = emptyList(),
    /** Perfume pages only. */
    val title: String = "",
    val brand: String = "",
    /** Perfume pages: whether the page's own big title is on screen. */
    val titleVisible: Boolean = true,
    /** Applied refinements from the page's InstantSearch uiState; null if unavailable. */
    val refinements: List<Refinement>? = null,
) {
    val isSearch get() = page == "search"

    fun panel(title: String): Panel? = panels.firstOrNull { it.title.equals(title, ignoreCase = true) }

    val sortLabel: String?
        get() = sortOptions.firstOrNull { it.value == sortSelected }?.label

    /**
     * Every refinement currently applied. Prefers the uiState list, which still
     * holds values the site drops from its facet lists at 0 results.
     */
    val activeFilters: List<ActiveFilter>
        get() = refinements?.map { r -> ActiveFilter(r.label, r.panel, ref = r) } ?: panels.flatMap { p ->
            when (p.type) {
                "range" -> {
                    val r = p.range
                    if (r == null || (r.from == null && r.to == null)) emptyList()
                    else listOf(ActiveFilter("${p.title} ${p.summary}", p.title, panel = p))
                }
                "radio" -> p.items.drop(1).filter { it.checked }
                    .map { ActiveFilter("${p.title}: ${it.label}", p.title, panel = p, item = it) }
                "rating" -> p.items.filter { it.checked }
                    .map { ActiveFilter("Rating ${it.label}", p.title, panel = p, item = it) }
                else -> p.items.filter { it.checked }.map { ActiveFilter(it.label, p.title, panel = p, item = it) }
            }
        }

    companion object {
        fun parse(json: String): PageState {
            val o = JSONObject(json)
            val sort = o.optJSONObject("sort")
            return PageState(
                page = o.optString("page", "other"),
                url = o.optString("url"),
                total = o.optIntOrNull("total"),
                hits = o.optInt("hits"),
                hitList = o.optJSONArray("hitList").objects().mapNotNull(Hit::parse),
                query = o.optString("query"),
                sortOptions = sort?.optJSONArray("options").objects().map {
                    SortOption(it.optString("value"), it.optString("label"))
                },
                sortSelected = sort?.optString("selected"),
                panels = o.optJSONArray("panels").objects().map(Panel::parse),
                title = o.optString("title"),
                brand = o.optString("brand"),
                titleVisible = o.optBoolean("titleVisible", true),
                refinements = o.optJSONArray("refinements")?.objects()?.map {
                    Refinement(
                        kind = it.optString("kind"),
                        attribute = it.optString("attribute"),
                        value = it.optString("value"),
                        panel = it.optString("panel"),
                        label = it.optString("label"),
                        without = it.optIntOrNull("without"),
                    )
                },
            )
        }
    }
}

data class SortOption(val value: String, val label: String)

data class FacetItem(val value: String, val label: String, val count: Int?, val checked: Boolean)

data class RangeState(val min: Int?, val max: Int?, val from: Int?, val to: Int?)

data class Panel(
    val title: String,
    val type: String, // list | radio | rating | range
    val items: List<FacetItem>,
    val searchable: Boolean,
    val canShowMore: Boolean,
    val showingMore: Boolean,
    val query: String,
    val range: RangeState?,
) {
    /** Short description of the current value, for collapsed rows. */
    val summary: String
        get() = when (type) {
            "range" -> {
                val r = range
                when {
                    r == null || (r.from == null && r.to == null) -> "Any"
                    r.from != null && r.to != null -> "${r.from}–${r.to}"
                    r.from != null -> "From ${r.from}"
                    else -> "Until ${r.to}"
                }
            }
            "radio" -> items.drop(1).firstOrNull { it.checked }?.label ?: "Any"
            else -> items.filter { it.checked }.map { it.label }.let { sel ->
                when (sel.size) {
                    0 -> "Any"
                    1, 2 -> sel.joinToString(", ")
                    else -> "${sel[0]} +${sel.size - 1}"
                }
            }
        }

    companion object {
        fun parse(o: JSONObject) = Panel(
            title = o.optString("title"),
            type = o.optString("type", "list"),
            items = o.optJSONArray("items").objects().map {
                FacetItem(
                    value = it.optString("value"),
                    label = it.optString("label"),
                    count = it.optIntOrNull("count"),
                    checked = it.optBoolean("checked"),
                )
            },
            searchable = o.optBoolean("searchable"),
            canShowMore = o.optBoolean("canShowMore"),
            showingMore = o.optBoolean("showingMore"),
            query = o.optString("query"),
            range = o.optJSONObject("range")?.let {
                RangeState(it.optIntOrNull("min"), it.optIntOrNull("max"), it.optIntOrNull("from"), it.optIntOrNull("to"))
            },
        )
    }
}

/**
 * One uiState refinement: kind is refinementList | numericMenu | range | ratingMenu | ….
 * [without] = result count if this refinement were removed, when the page knows it.
 */
data class Refinement(
    val kind: String,
    val attribute: String,
    val value: String,
    val panel: String,
    val label: String,
    val without: Int? = null,
)

/** A search result card. */
data class Hit(
    val url: String,
    val name: String,
    val brand: String,
    val year: String?,
    val gender: String?,
    val thumb: String?,
) {
    /** "2014 · Female" */
    val meta: String
        get() = listOfNotNull(year, gender?.replaceFirstChar { it.uppercase() }).joinToString(" · ")

    companion object {
        fun parse(o: JSONObject): Hit? {
            val url = o.optString("url").ifEmpty { return null }
            return Hit(
                url = url,
                name = o.optString("name"),
                brand = o.optString("brand"),
                year = o.optString("year").takeIf { it.isNotEmpty() && it != "null" },
                gender = o.optString("gender").takeIf { it.isNotEmpty() && it != "null" },
                thumb = o.optString("thumb").takeIf { it.startsWith("http") },
            )
        }
    }
}

/**
 * One applied filter, as shown in chips and the no-results list. Removed via [ref]
 * when known, otherwise via the panel's DOM control ([item] null means a range).
 */
data class ActiveFilter(
    val label: String,
    val panelTitle: String,
    val ref: Refinement? = null,
    val panel: Panel? = null,
    val item: FacetItem? = null,
)

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
