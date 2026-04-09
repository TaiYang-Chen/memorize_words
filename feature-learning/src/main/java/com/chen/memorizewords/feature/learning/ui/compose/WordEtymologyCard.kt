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
import com.chen.memorizewords.domain.model.words.word.WordRoot

@Composable
fun WordEtymologyCard(roots: List<WordRoot>) {
    Column(
        modifier = Modifier.padding(24.dp)
    ) {
        roots.forEachIndexed { index, root ->
            EtymologyItem(
                prefix = root.rootWord,
                explanation = root.coreMeaning
            )
            if (index < roots.size - 1) {
                Spacer(modifier = Modifier.height(12.dp))
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
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.width(60.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = explanation,
                fontSize = 14.sp,
                color = Color.Black
            )
        }
    }
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
