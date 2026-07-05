package com.chen.memorizewords.feature.learning.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.memorizewords.domain.word.model.word.WordRoot

@Composable
fun WordEtymologyCard(roots: List<WordRoot>) {
    Column(
        modifier = Modifier.padding(WordEtymologyCardDimensions.containerPadding)
    ) {
        roots.forEachIndexed { index, root ->
            EtymologyItem(
                prefix = root.rootWord,
                explanation = root.coreMeaning
            )
            if (index < roots.size - 1) {
                Spacer(modifier = Modifier.height(WordEtymologyCardDimensions.itemSpacing))
            }
        }
    }
}

@Composable
fun EtymologyItem(
    prefix: String,
    explanation: String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = prefix,
            fontSize = WordEtymologyCardDimensions.prefixTextSize,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.width(WordEtymologyCardDimensions.prefixWidth)
        )

        Spacer(modifier = Modifier.width(WordEtymologyCardDimensions.contentSpacing))

        Column {
            Text(
                text = explanation,
                fontSize = WordEtymologyCardDimensions.explanationTextSize,
                color = Color.Black
            )
        }
    }
}

private object WordEtymologyCardDimensions {
    val containerPadding = 24.dp
    val itemSpacing = 12.dp
    val prefixWidth = 60.dp
    val contentSpacing = 12.dp
    val prefixTextSize = 16.sp
    val explanationTextSize = 14.sp
}

@Preview(showBackground = true, showSystemUi = false)
@Composable
fun WordEtymologyCardPreview() {
    val sampleRoots = listOf(
        WordRoot(
            id = 101,
            rootWord = "pro",
            coreMeaning = "forward, in front",
            etymology = null,
            sourceLanguage = "Latin"
        ),
        WordRoot(
            id = 102,
            rootWord = "gress",
            coreMeaning = "to step, to go",
            etymology = null,
            sourceLanguage = "Latin"
        ),
        WordRoot(
            id = 103,
            rootWord = "ive",
            coreMeaning = "pertaining to",
            etymology = null,
            sourceLanguage = "Latin"
        )
    )

    WordEtymologyCard(roots = sampleRoots)
}
