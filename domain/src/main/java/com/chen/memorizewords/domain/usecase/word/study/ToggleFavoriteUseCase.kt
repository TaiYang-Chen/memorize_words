package com.chen.memorizewords.domain.usecase.word.study

import com.chen.memorizewords.domain.model.study.favorites.WordFavorites
import com.chen.memorizewords.domain.model.words.word.Word
import com.chen.memorizewords.domain.repository.word.FavoritesRepository
import com.chen.memorizewords.domain.repository.word.WordRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(
        word: Word
    ) {
        if (favoritesRepository.isFavorite(word.id)) {
            favoritesRepository.removeFavorite(word.id)
        } else {

            var str = ""
            wordRepository.getWordDefinitions(word.id).map { definitions ->
                str += "${definitions.partOfSpeech} ${definitions.meaningChinese} "
            }

            favoritesRepository.addFavorite(
                WordFavorites(
                    wordId = word.id,
                    word = word.word,
                    definitions = str,
                    phonetic = word.phoneticUS,
                    addedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                )
            )
        }
    }
}
