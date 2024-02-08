package su.sres.securesms.search

import su.sres.securesms.database.model.ThreadRecord

data class ThreadSearchResult(val results: List<ThreadRecord>, val query: String)