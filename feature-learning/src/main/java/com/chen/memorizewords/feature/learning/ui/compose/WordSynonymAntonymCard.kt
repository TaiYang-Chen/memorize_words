package com.chen.memorizewords.feature.learning.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.memorizewords.feature.learning.ui.visibleRelationWords

@Composable
fun WordSynonymAntonymCard(
    synonyms: List<String>,
    antonyms: List<String>,
    modifier: Modifier = Modifier
) {
    val visibleSynonyms = synonyms.visibleRelationWords()
    val visibleAntonyms = antonyms.visibleRelationWords()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WordSynonymAntonymCardDimensions.containerPadding),
        horizontalAlignment = Alignment.Start
    ) {
        if (visibleSynonyms.isNotEmpty()) {
            SynonymAntonymSection(
                title = "同义词",
                words = visibleSynonyms,
                tagColor = Color(0xFFE6F7FF)
            )
        }

        if (visibleSynonyms.isNotEmpty() && visibleAntonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(WordSynonymAntonymCardDimensions.sectionSpacing))
        }

        if (visibleAntonyms.isNotEmpty()) {
            SynonymAntonymSection(
                title = "反义词",
                words = visibleAntonyms,
                tagColor = Color(0xFFFFF0F5)
            )
        }
    }
}

@Composable
private fun SynonymAntonymSection(
    title: String,
    words: List<String>,
    tagColor: Color
) {
    Column {
        Text(
            text = title,
            fontSize = WordSynonymAntonymCardDimensions.titleTextSize,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = WordSynonymAntonymCardDimensions.titleBottomPadding)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WordSynonymAntonymCardDimensions.tagSpacing),
            verticalArrangement = Arrangement.spacedBy(WordSynonymAntonymCardDimensions.tagSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            words.forEach { word ->
                WordTag(
                    word = word,
                    tagColor = tagColor
                )
            }
        }
    }
}

@Composable
private fun WordTag(
    word: String,
    tagColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(WordSynonymAntonymCardDimensions.tagCorner))
            .background(tagColor)
            .padding(
                horizontal = WordSynonymAntonymCardDimensions.tagHorizontalPadding,
                vertical = WordSynonymAntonymCardDimensions.tagVerticalPadding
            )
    ) {
        Text(
            text = word,
            fontSize = WordSynonymAntonymCardDimensions.tagTextSize,
            fontWeight = FontWeight.Normal,
            color = Color.Black
        )
    }
}

private object WordSynonymAntonymCardDimensions {
    val containerPadding = 20.dp
    val sectionSpacing = 24.dp
    val titleBottomPadding = 12.dp
    val tagSpacing = 8.dp
    val tagCorner = 8.dp
    val tagHorizontalPadding = 12.dp
    val tagVerticalPadding = 8.dp
    val titleTextSize = 16.sp
    val tagTextSize = 14.sp
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewWordSynonymAntonymCard() {
    WordSynonymAntonymCard(
        synonyms = listOf("progress", "advance", "development", "growth", "improvement"),
        antonyms = listOf("regress", "decline", "retreat")
    )
}
