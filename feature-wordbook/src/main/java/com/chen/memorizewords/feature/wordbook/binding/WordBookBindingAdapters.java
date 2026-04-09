package com.chen.memorizewords.feature.wordbook.binding;

import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

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
        String raw = rawUrl == null ? "" : rawUrl.trim();
        if (raw.isEmpty()) {
            return null;
        }
        String lower = raw.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return raw
                    .replace("http://localhost", "http://10.0.2.2")
                    .replace("https://localhost", "https://10.0.2.2");
        }
        if (raw.startsWith("/")) {
            return "http://10.0.2.2:8080" + raw;
        }
        return "http://10.0.2.2:8080/" + raw;
    }
}
