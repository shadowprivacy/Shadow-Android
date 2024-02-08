package su.sres.securesms.search

import su.sres.securesms.recipients.Recipient

data class ContactSearchResult(val results: List<Recipient>, val query: String)