package com.chen.memorizewords.feature.wordbook.binding;

import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import com.chen.memorizewords.core.ui.image.WordBookCoverUrlNormalizer;
import com.chen.memorizewords.feature.wordbook.R;

import coil.Coil;
import coil.ImageLoader;
import coil.request.ImageRequest;

public final class WordBookBindingAdapters {

    private WordBookBindingAdapters() {
    }

    @BindingAdapter("imageUrl")
    public static void bindImageUrl(ImageView view, String rawUrl) {
        ImageLoader imageLoader = Coil.imageLoader(view.getContext());
        ImageRequest request = new ImageRequest.Builder(view.getContext())
                .data(toDisplayUrl(rawUrl))
                .target(view)
                .crossfade(true)
                .placeholder(R.drawable.module_wordbook_ic_book)
                .error(R.drawable.module_wordbook_ic_book)
                .fallback(R.drawable.module_wordbook_ic_book)
                .build();
        imageLoader.enqueue(request);
    }

    private static String toDisplayUrl(String rawUrl) {
        return WordBookCoverUrlNormalizer.toDisplayUrl(rawUrl);
    }
}
