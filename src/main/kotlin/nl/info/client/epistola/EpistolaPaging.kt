/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.client.epistola

import app.epistola.client.jakarta.model.PageMeta

/** The largest page size Epistola's contract allows. */
internal const val EPISTOLA_PAGE_SIZE = 100

/** Epistola returns at most [EPISTOLA_PAGE_SIZE] items per request, so a longer list is read page by page. */
internal fun <T> readEveryPage(readPage: (pageNumber: Int) -> Pair<List<T>?, PageMeta?>): List<T> {
    val items = mutableListOf<T>()
    var pageNumber = 0
    do {
        val (pageItems, page) = readPage(pageNumber)
        items += pageItems.orEmpty()
        pageNumber++
    } while (pageNumber < (page?.totalPages ?: 0))
    return items
}
