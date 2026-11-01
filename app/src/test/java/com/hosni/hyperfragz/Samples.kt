package com.hosni.hyperfragz

/**
 * Sample data for the screenshot tests, shaped like what bridge.js and extract.js
 * report. It mirrors the content of the design/ PNGs so renders can be compared
 * with the spec side by side. Usernames and review text are made up: never paste
 * real Fragrantica members' names or reviews here.
 */
object Samples {
    private const val P = "https://www.fragrantica.com/perfume"

    private fun hit(name: String, year: String, slug: String) =
        Hit("$P/Yves-Saint-Laurent/$slug.html", name, "Yves Saint Laurent", year, "female", thumb = null)

    val hits = listOf(
        hit("Black Opium", "2014", "Black-Opium-25324"),
        hit("Libre", "2019", "Libre-56077"),
        hit("Mon Paris", "2016", "Mon-Paris-38914"),
        hit("Libre Intense", "2020", "Libre-Intense-62318"),
        hit("Opium", "1977", "Opium-1460"),
        hit("Paris", "1983", "Paris-1505"),
    )

    private val sort = listOf(
        SortOption("popular", "Most popular"),
        SortOption("newest", "Newest"),
        SortOption("rating", "Top rated"),
    )

    private fun items(vararg labels: String, checked: Set<String> = emptySet()) =
        labels.mapIndexed { i, l -> FacetItem(l.lowercase(), l, count = 1200 - i * 97, checked = l in checked) }

    private fun panel(title: String, type: String, items: List<FacetItem>, searchable: Boolean = false, range: RangeState? = null) =
        Panel(title, type, items, searchable, canShowMore = false, showingMore = false, query = "", range = range)

    private val panels = listOf(
        panel("Gender", "list", items("Female", "Unisex", "Male", checked = setOf("Female"))),
        panel("Notes", "list", items("Vanilla", "Bergamot", "Rose", "Sandalwood", "Patchouli", "Jasmine"), searchable = true),
        panel("Groups", "list", items("Floral", "Woody", "Amber", "Gourmand", "Fresh", "Citrus")),
        panel("Designers", "list", items("Yves Saint Laurent", "Chanel", "Dior", checked = setOf("Yves Saint Laurent")), searchable = true),
        panel("Minimum votes", "radio", items("Any", "100+", "1000+", checked = setOf("100+"))),
        panel("Rating", "rating", items("4 & up", "3 & up")),
        panel("Year", "range", emptyList(), range = RangeState(1920, 2026, null, null)),
        panel("Perfumers", "list", items("Dominique Ropion", "Anne Flipo"), searchable = true),
    )

    private val refinements = listOf(
        Refinement("refinementList", "gender", "female", "Gender", "Female", without = 4120),
        Refinement("refinementList", "designer", "yves saint laurent", "Designers", "Yves Saint Laurent", without = 18_204),
        Refinement("numericMenu", "votes", "100", "Minimum votes", "100+", without = 212),
    )

    /** design/1 · Results */
    val results = PageState(
        page = "search",
        url = "https://www.fragrantica.com/search/",
        total = 64,
        hits = hits.size,
        hitList = hits,
        sortOptions = sort,
        sortSelected = "popular",
        panels = panels,
        refinements = refinements,
    )

    /** design/3 · No results */
    val noResults = results.copy(
        total = 0,
        hits = 0,
        hitList = emptyList(),
        refinements = listOf(
            refinements[0],
            Refinement("refinementList", "notes", "leather", "Notes", "Leather", without = 3),
            refinements[1].copy(without = 41),
            Refinement("numericMenu", "votes", "1000", "Minimum votes", "1000+", without = 7),
        ),
    )

    private fun related(name: String, brand: String, yes: Int?, no: Int?) =
        Related("$P/x/${name.replace(' ', '-')}.html", name, brand, image = null, yes, no)

    /** design/4 · Fragrance detail, fully loaded. */
    val detail = Detail(
        url = "$P/Yves-Saint-Laurent/Black-Opium-25324.html",
        name = "Black Opium",
        brand = "Yves Saint Laurent",
        image = null,
        family = "Amber Vanilla",
        audience = "women",
        year = "2014",
        perfumers = "Nathalie Lorson, Marie Salamagne, Olivier Cresp, Honorine Blanc",
        rating = Rating(3.98, 21_544, listOf(Count("love", 7200), Count("like", 6100), Count("ok", 3300), Count("dislike", 2100), Count("hate", 900))),
        accords = listOf(
            Accord("vanilla", 100f), Accord("sweet", 82f), Accord("coffee", 71f),
            Accord("warm spicy", 58f), Accord("white floral", 44f), Accord("fruity", 38f),
        ),
        notes = Notes(
            top = listOf("Pear", "Pink Pepper", "Orange Blossom"),
            middle = listOf("Coffee", "Jasmine", "Bitter Almond", "Licorice"),
            base = listOf("Vanilla", "Patchouli", "Cedar", "Cashmere Wood"),
            flat = emptyList(),
        ),
        longevity = listOf(Count("very weak", 310), Count("weak", 620), Count("moderate", 2900), Count("long lasting", 4800), Count("eternal", 1700)),
        sillage = listOf(Count("intimate", 520), Count("moderate", 3100), Count("strong", 3900), Count("enormous", 1400)),
        wear = listOf(
            Count("winter", 92), Count("spring", 34), Count("summer", 12), Count("fall", 88),
            Count("day", 41), Count("night", 96),
        ),
        pros = listOf(Opinion("Lasts all day", 412, 38), Opinion("Cosy and warm", 301, 52), Opinion("Gets compliments", 288, 61)),
        cons = listOf(Opinion("Very sweet", 356, 120), Opinion("Smells like many others", 198, 140)),
        similar = listOf(
            related("Libre", "Yves Saint Laurent", 820, 140),
            related("La Vie Est Belle", "Lancôme", 640, 210),
            related("Good Girl", "Carolina Herrera", 590, 260),
        ),
        alsoLike = listOf(
            related("Alien", "Mugler", null, null),
            related("Hypnotic Poison", "Dior", null, null),
            related("Angel", "Mugler", null, null),
        ),
        reviews = Reviews(
            total = 2,
            sort = "newest",
            items = listOf(
                Review("r1", "sample_user_one", "2026-09-14", "love", listOf("Winter", "Night"), "Sample review text for the screenshot test. Warm coffee and vanilla, lasts well into the evening."),
                Review("r2", "sample_user_two", "2026-08-02", "ok", emptyList(), "Second sample review. Sweeter than expected; better in cold weather."),
            ),
        ),
        complete = true,
    )

    /** The instant header plus skeletons, while the hidden page is still loading. */
    val detailLoading = Detail.preview(hits[0])
}
