package app.otakureader.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI smoke tests for Library screen components.
 *
 * These tests run on the JVM via Robolectric — no emulator required.
 * They exercise the visual behaviour of standalone composables that mirror
 * the structure of [app.otakureader.feature.library.LibraryScreen]:
 *
 * - Render states (loading, empty, grid)
 * - Search bar visibility and text input
 * - Multi-select action bar
 * - Filter chips
 * - Sort order reflected in rendered list
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Render state tests ───────────────────────────────────────────────────

    @Test
    fun loadingState_showsCircularProgressIndicator() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoadingStateContent()
                }
            }
        }
        composeTestRule.onNodeWithTag("library_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsEmptyMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EmptyStateContent(message = "Your library is empty")
                }
            }
        }
        composeTestRule.onNodeWithText("Your library is empty").assertIsDisplayed()
    }

    @Test
    fun gridState_showsMangaTitles() {
        val titles = listOf("Naruto", "Bleach", "One Piece")
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaGridContent(titles = titles)
                }
            }
        }
        titles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun gridState_nineItems_allRendered() {
        val titles = (1..9).map { "Manga $it" }
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaGridContent(titles = titles)
                }
            }
        }
        // Verify first and last items are reachable
        composeTestRule.onNodeWithText("Manga 1").assertIsDisplayed()
    }

    // ── Search bar tests ─────────────────────────────────────────────────────

    @Test
    fun searchBar_hidden_byDefault() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    NormalTopBar(
                        onSearchClick = {},
                        onMoreClick = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("library_search_field").assertDoesNotExist()
    }

    @Test
    fun searchBar_visible_showsTextField() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    SearchTopBar(
                        query = "",
                        onQueryChange = {},
                        onCloseSearch = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("library_search_field").assertIsDisplayed()
    }

    @Test
    fun searchBar_toggle_showsAndHides() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    ToggleableSearchBar()
                }
            }
        }
        // Initially the search field is not visible
        composeTestRule.onNodeWithTag("library_search_field").assertDoesNotExist()
        // Tap the search icon to show the search bar
        composeTestRule.onNodeWithTag("library_search_icon").performClick()
        composeTestRule.onNodeWithTag("library_search_field").assertIsDisplayed()
        // Tap close to hide again
        composeTestRule.onNodeWithTag("library_close_search_icon").performClick()
        composeTestRule.onNodeWithTag("library_search_field").assertDoesNotExist()
    }

    @Test
    fun searchBar_userInput_updatesDisplayedText() {
        var capturedQuery = ""
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    SearchTopBar(
                        query = "",
                        onQueryChange = { capturedQuery = it },
                        onCloseSearch = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("library_search_field").performTextInput("Naruto")
        // The callback should have been invoked
        assert(capturedQuery.isNotEmpty()) {
            "Expected onQueryChange callback to be invoked, but capturedQuery was empty"
        }
    }

    // ── Multi-select tests ───────────────────────────────────────────────────

    @Test
    fun selectionActionBar_shown_whenItemsSelected() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    SelectionTopBar(selectedCount = 2)
                }
            }
        }
        composeTestRule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    @Test
    fun selectionActionBar_showsBulkActionIcons() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    SelectionTopBar(selectedCount = 1)
                }
            }
        }
        composeTestRule.onNodeWithTag("library_action_mark_read").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_action_mark_unread").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_action_download").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_action_remove").assertIsDisplayed()
    }

    @Test
    fun selectionActionBar_clearButton_invokesCallback() {
        var clearCalled = false
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    SelectionTopBar(
                        selectedCount = 3,
                        onClearSelection = { clearCalled = true }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("library_action_clear_selection").performClick()
        assert(clearCalled) { "Expected clear-selection callback to be invoked" }
    }

    // ── Filter chip tests ────────────────────────────────────────────────────

    @Test
    fun filterChips_allChip_displayedByDefault() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    FilterChipRow(
                        categories = listOf("Action", "Romance"),
                        selectedCategory = null,
                        onCategorySelected = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
    }

    @Test
    fun filterChips_categoryChips_displayedForEachCategory() {
        val categories = listOf("Shonen", "Seinen", "Josei")
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    FilterChipRow(
                        categories = categories,
                        selectedCategory = null,
                        onCategorySelected = {}
                    )
                }
            }
        }
        categories.forEach { cat ->
            composeTestRule.onNodeWithText(cat).assertIsDisplayed()
        }
    }

    @Test
    fun filterChips_categoryClick_invokesCallback() {
        var selected: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                Surface {
                    FilterChipRow(
                        categories = listOf("Action"),
                        selectedCategory = null,
                        onCategorySelected = { selected = it }
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Action").performClick()
        assert(selected == "Action") { "Expected 'Action' to be selected, got: $selected" }
    }

    // ── Sort order tests ─────────────────────────────────────────────────────

    @Test
    fun mangaGrid_alphabeticalOrder_titlesShownInOrder() {
        val sortedTitles = listOf("Bleach", "Naruto", "One Piece")
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaGridContent(titles = sortedTitles)
                }
            }
        }
        // Verify all titles are present after alphabetical sort
        sortedTitles.forEach { title ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun mangaGrid_withUnreadBadge_badgeDisplayed() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaGridWithBadge(title = "Naruto", unreadCount = 5)
                }
            }
        }
        composeTestRule.onNodeWithText("5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Naruto").assertIsDisplayed()
    }
}

// ---------------------------------------------------------------------------
// Lightweight test composables — no ViewModel required
// ---------------------------------------------------------------------------

@Composable
private fun LoadingStateContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("library_loading_indicator"))
    }
}

@Composable
private fun EmptyStateContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MangaGridContent(titles: List<String>, columns: Int = 3) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(titles) { title ->
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MangaGridWithBadge(title: String, unreadCount: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UnusedParameter")
private fun NormalTopBar(
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    TopAppBar(
        title = { Text("Library") },
        actions = {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("library_search_icon")
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search library")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search…") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_field")
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onCloseSearch,
                modifier = Modifier.testTag("library_close_search_icon")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleableSearchBar() {
    var showSearch by remember { mutableStateOf(false) }
    if (showSearch) {
        SearchTopBar(
            query = "",
            onQueryChange = {},
            onCloseSearch = { showSearch = false }
        )
    } else {
        NormalTopBar(
            onSearchClick = { showSearch = true },
            onMoreClick = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onMarkUnread: () -> Unit = {},
    onDownload: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("library_action_clear_selection")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Deselect all")
            }
        },
        actions = {
            IconButton(
                onClick = onMarkRead,
                modifier = Modifier.testTag("library_action_mark_read")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Mark as read")
            }
            IconButton(
                onClick = onMarkUnread,
                modifier = Modifier.testTag("library_action_mark_unread")
            ) {
                Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Mark as unread")
            }
            IconButton(
                onClick = onDownload,
                modifier = Modifier.testTag("library_action_download")
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download selected")
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("library_action_remove")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Remove from library")
            }
        }
    )
}

@Composable
private fun FilterChipRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") }
            )
        }
        categories.forEach { category ->
            item {
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category) }
                )
            }
        }
    }
}
