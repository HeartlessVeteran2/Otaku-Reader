// Stub matching tachiyomiorg/extensions-lib — used at compile time only.
package eu.kanade.tachiyomi.source.model

@Suppress("unused")
interface SChapter {
    var url: String
    var name: String
    var date_upload: Long
    var chapter_number: Float
    var scanlator: String?

    companion object {
        fun create(): SChapter = SChapterImpl()
    }
}

/** Minimal mutable implementation used when creating SChapter instances in the host app. */
internal class SChapterImpl : SChapter {
    override var url: String = ""
    override var name: String = ""
    override var date_upload: Long = 0L
    override var chapter_number: Float = -1f
    override var scanlator: String? = null
}
