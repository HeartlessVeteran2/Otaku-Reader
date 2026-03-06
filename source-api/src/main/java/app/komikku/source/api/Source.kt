package app.komikku.source.api

import app.komikku.domain.model.Manga

interface Source {
    suspend fun search(query: String): List<Manga>
}
