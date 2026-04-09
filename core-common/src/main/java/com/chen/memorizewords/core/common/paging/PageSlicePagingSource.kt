package com.chen.memorizewords.core.common.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.chen.memorizewords.domain.model.common.PageSlice

class PageSlicePagingSource<T : Any>(
    private val loadPage: suspend (pageIndex: Int, pageSize: Int) -> PageSlice<T>
) : PagingSource<Int, T>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val pageIndex = params.key ?: 0
        return runCatching {
            val page = loadPage(pageIndex, params.loadSize)
            LoadResult.Page(
                data = page.items,
                prevKey = if (pageIndex == 0) null else pageIndex - 1,
                nextKey = if (page.hasNext) pageIndex + 1 else null
            )
        }.getOrElse { error ->
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }
}
