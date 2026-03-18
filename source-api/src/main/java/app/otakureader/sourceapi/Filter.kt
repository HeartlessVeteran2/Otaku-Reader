package app.otakureader.sourceapi

sealed class Filter<T>(val name: String, var state: T) {

    class Header(name: String) : Filter<Any>(name, Unit)

    class Separator(name: String = "") : Filter<Any>(name, Unit)

    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) :
        Filter<Int>(name, state)

    abstract class Text(name: String, state: String = "") : Filter<String>(name, state)

    abstract class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    abstract class TriState(name: String, state: Int = STATE_IGNORE) : Filter<Int>(name, state) {
        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    abstract class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)

    abstract class Sort(
        name: String,
        val values: Array<String>,
        state: Selection? = null,
    ) : Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
}

class FilterList(val filters: List<Filter<*>> = emptyList()) {
    constructor(vararg filters: Filter<*>) : this(filters.toList())

    /**
     * Returns `true` when at least one filter has been changed from its default state.
     * Recurses into [Filter.Group] children.
     */
    fun hasActiveFilters(): Boolean = filters.any { it.isActive() }
}

/**
 * Checks whether a single filter has a non-default state.
 * For groups, recurses into children.
 */
fun Filter<*>.isActive(): Boolean = when (this) {
    is Filter.Select<*> -> state != 0
    is Filter.Text -> state.isNotBlank()
    is Filter.CheckBox -> state
    is Filter.TriState -> state != Filter.TriState.STATE_IGNORE
    is Filter.Sort -> state != null
    is Filter.Group<*> -> {
        val children = state.filterIsInstance<Filter<*>>()
        children.any { it.isActive() }
    }
    else -> false
}

/**
 * Concrete filter implementations for use by source adapters and the app UI.
 * Extensions subclass the abstract [Filter] types; these classes provide
 * instantiable versions the app can create when converting from external
 * filter representations (e.g., Tachiyomi compat layer).
 */
object Filters {
    class SelectFilter(name: String, values: Array<String>, state: Int = 0) :
        Filter.Select<String>(name, values, state)

    class TextFilter(name: String, state: String = "") :
        Filter.Text(name, state)

    class CheckBoxFilter(name: String, state: Boolean = false) :
        Filter.CheckBox(name, state)

    class TriStateFilter(name: String, state: Int = Filter.TriState.STATE_IGNORE) :
        Filter.TriState(name, state)

    class GroupFilter(name: String, state: List<Filter<*>>) :
        Filter.Group<Filter<*>>(name, state)

    class SortFilter(name: String, values: Array<String>, state: Filter.Sort.Selection? = null) :
        Filter.Sort(name, values, state)
}
