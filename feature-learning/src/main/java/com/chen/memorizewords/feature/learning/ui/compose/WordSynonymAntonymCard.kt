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

@Composable
fun WordSynonymAntonymCard(
    synonyms: List<String>,
    antonyms: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 显示同义词部分
        if (synonyms.isNotEmpty()) {
            SynonymAntonymSection(
                title = "同义词",
                words = synonyms,
                tagColor = Color(0xFFE6F7FF)  // 浅青色，与图片完全一致
            )
        }

        // 间距
        if (synonyms.isNotEmpty() && antonyms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 显示反义词部分
        if (antonyms.isNotEmpty()) {
            SynonymAntonymSection(
                title = "反义词",
                words = antonyms,
                tagColor = Color(0xFFFFF0F5)  // 浅粉色，与图片完全一致
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
        // 标题
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ✅ 自适应标签布局
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            .clip(RoundedCornerShape(8.dp))
            .background(tagColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = word,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color.Black
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewWordSynonymAntonymCard() {
    // 1. 创建模拟的同义词列表
    val sampleSynonyms = listOf(
        "progress", "advance", "development", "growth", "improvement"
    )

    // 2. 创建模拟的反义词列表
    val sampleAntonyms = listOf(
        "regress", "decline", "retreat"
    )

    // 3. 将模拟数据传入 WordSynonymAntonymCard 组件
    WordSynonymAntonymCard(
        synonyms = sampleSynonyms,
        antonyms = sampleAntonyms
    )
}
