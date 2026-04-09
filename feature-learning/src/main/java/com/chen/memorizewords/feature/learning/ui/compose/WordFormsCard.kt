package com.chen.memorizewords.feature.learning.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chen.memorizewords.domain.model.words.enums.FormType
import com.chen.memorizewords.domain.model.words.word.WordForm
import kotlin.collections.forEachIndexed

@Composable
fun WordFormsCard(list: List<WordForm>) {
    // 定义与图片相似的颜色
    val cardColors = listOf(
        Color(0xFFE3F2FD), // 浅蓝色
        Color(0xFFF3E5F5), // 浅紫色
        Color(0xFFFFF8E1)  // 浅黄色
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        list.forEachIndexed { index, wordForm ->
            // 循环使用颜色，如果单词形式超过3个，从头开始
            val cardColor = cardColors[index % cardColors.size]

            WordFormItem(
                wordForm = wordForm,
                cardColor = cardColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WordFormItem(
    wordForm: WordForm,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .background(cardColor)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：词性标签
            Text(
                text = wordForm.formType.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.width(40.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 中间：单词
            Text(
                text = wordForm.formType.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )

            // 右侧：中文释义
            Text(
                text = wordForm.formText,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun WordFormsCardExample() {
    // 1. 创建一个完全匹配 WordForm 结构和 WordFormItem 显示逻辑的模拟列表
    val sampleWordForms = listOf(
        // 第一个单词形式: progress (名词)
        // 这个对象需要提供 "n."、"progress" 和 "进步；发展"
        // 但是现有 WordForm 结构无法同时提供这三者。
        // 我们必须根据 WordFormItem 的现有代码进行妥协来创建预览。
        // WordFormItem 显示: formType, formType.name, formText
        // 为了预览正确，我们不得不“滥用”字段：
        WordForm(
            id = 1,
            wordId = 1,
            formWordId = 10,
            // 假设 formType 是一个可以自定义 text 的类，或者我们直接修改 WordFormItem
            formType = FormType.FORMAL, // 左侧显示 "n."
            formText = "进步；发展" // 右侧显示中文
            // 问题：中间的单词 "progress" 没有字段可以放
        ),
        WordForm(
            id = 2,
            wordId = 1,
            formWordId = 11,
            formType = FormType.FORMAL, // 左侧显示 "n."
            formText = "进步的；渐进的" // 右侧显示中文
            // 问题：中间的单词 "progressive" 没有字段可以放
        ),
        WordForm(
            id = 3,
            wordId = 1,
            formWordId = 12,
            formType = FormType.FORMAL, // 左侧显示 "n."
            formText = "逐渐地" // 右侧显示中文
            // 问题：中间的单词 "progressively" 没有字段可以放
        )
    )

    // 2. 将模拟数据传入 WordFormsCard 组件
    WordFormsCard(list = sampleWordForms)
}