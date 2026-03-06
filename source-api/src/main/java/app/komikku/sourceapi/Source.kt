package app.komikku.sourceapi

interface Source {
    val id: String
    val name: String
    val lang: String
    val isNsfw: Boolean get() = false
}
