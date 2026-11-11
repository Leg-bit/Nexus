package com.nexus.media.ui

import java.util.Base64

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

fun folderName(path: String) = path.trimEnd('/').substringAfterLast('/')

fun enc(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
fun dec(s: String): String = String(Base64.getUrlDecoder().decode(s))
