package com.nexus.media.data.repository

import com.nexus.media.data.local.entities.MediaEntity

/** key: name | artist | album | date | duration | size | resolution */
data class SortSetting(val key: String, val asc: Boolean)

/** Sensible first direction for a key: text A-Z, everything else biggest/newest first. */
fun defaultAsc(key: String): Boolean = key == "name" || key == "artist" || key == "album"

val videoSortOptions = listOf(
    "name" to "Nama", "date" to "Tanggal ditambahkan", "duration" to "Durasi", "size" to "Ukuran", "resolution" to "Resolusi"
)
val musicSortOptions = listOf(
    "name" to "Judul", "artist" to "Artis", "album" to "Album", "date" to "Tanggal ditambahkan", "duration" to "Durasi", "size" to "Ukuran"
)

/** Case-insensitive compare that understands numbers: "Track 2" < "Track 10". */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var ie = i
            while (ie < a.length && a[ie].isDigit()) ie++
            var je = j
            while (je < b.length && b[je].isDigit()) je++
            val na = a.substring(i, ie).trimStart('0')
            val nb = b.substring(j, je).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = ie
            j = je
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

private fun byText(sel: (MediaEntity) -> String) = Comparator<MediaEntity> { a, b -> naturalCompare(sel(a), sel(b)) }

fun sortMedia(list: List<MediaEntity>, s: SortSetting): List<MediaEntity> {
    val title = byText { it.title }
    val cmp: Comparator<MediaEntity> = when (s.key) {
        "artist" -> byText { it.artist }.thenComparator { a, b -> naturalCompare(a.title, b.title) }
        "album" -> byText { it.album }.thenComparator { a, b -> naturalCompare(a.title, b.title) }
        "date" -> compareBy<MediaEntity> { it.dateAdded }.thenComparator { a, b -> naturalCompare(a.title, b.title) }
        "duration" -> compareBy<MediaEntity> { it.duration }
        "size" -> compareBy<MediaEntity> { it.size }
        "resolution" -> compareBy<MediaEntity> { it.width.toLong() * it.height }
        else -> title
    }
    return list.sortedWith(if (s.asc) cmp else cmp.reversed())
}
