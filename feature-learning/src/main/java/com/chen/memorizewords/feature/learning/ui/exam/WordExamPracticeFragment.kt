package com.chen.memorizewords.feature.learning.ui.exam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.domain.model.practice.ExamCategory
import com.chen.memorizewords.domain.model.practice.ExamItemLastResult
import com.chen.memorizewords.domain.model.practice.ExamQuestionType
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WordExamPracticeFragment : Fragment() {

    private val viewModel: WordExamPracticeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MaterialTheme {
                    WordExamPracticeScreen(
                        state = uiState,
                        onBack = { findNavController().navigateUp() },
                        onRetry = viewModel::load,
                        onToggleType = viewModel::toggleType,
                        onToggleCategory = viewModel::toggleCategory,
                        onStatusFilterChange = viewModel::setStatusFilter,
                        onShowAllAnswers = viewModel::showAllAnswers,
                        onHideAllAnswers = viewModel::hideAllAnswers,
                        onToggleAnswer = viewModel::toggleAnswer,
                        onSelectSingleChoice = viewModel::selectSingleChoice,
                        onToggleClozeChoice = viewModel::toggleClozeChoice,
                        onSelectMatchingLeft = viewModel::selectMatchingLeft,
                        onSelectMatchingRight = viewModel::selectMatchingRight,
                        onTranslationChange = viewModel::updateTranslation,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        if (activity?.isChangingConfigurations != true) {
            viewModel.finishSessionIfNeeded()
        }
        super.onDestroyView()
    }
}

@Composable
private fun WordExamPracticeScreen(
    state: WordExamPracticeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleType: (ExamQuestionType) -> Unit,
    onToggleCategory: (ExamCategory) -> Unit,
    onStatusFilterChange: (ExamStatusFilter) -> Unit,
    onShowAllAnswers: () -> Unit,
    onHideAllAnswers: () -> Unit,
    onToggleAnswer: (Long) -> Unit,
    onSelectSingleChoice: (Long, Int) -> Unit,
    onToggleClozeChoice: (Long, String) -> Unit,
    onSelectMatchingLeft: (Long, Int) -> Unit,
    onSelectMatchingRight: (Long, Int) -> Unit,
    onTranslationChange: (Long, String) -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                title = if (state.wordText.isBlank()) "\u771f\u9898\u7ec3\u4e60" else "${state.wordText} \u771f\u9898",
                onBack = onBack
            )

            if (state.isLoading && state.items.isEmpty()) {
                LoadingState()
                return@Column
            }

            if (state.errorMessage != null && state.items.isEmpty()) {
                ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SummaryCard(state = state)
                }

                if (state.isReadOnlyCache) {
                    item {
                        ReadOnlyBanner()
                    }
                }

                item {
                    FilterSection(
                        state = state,
                        onToggleType = onToggleType,
                        onToggleCategory = onToggleCategory,
                        onStatusFilterChange = onStatusFilterChange,
                        onShowAllAnswers = onShowAllAnswers,
                        onHideAllAnswers = onHideAllAnswers
                    )
                }

                if (state.errorMessage != null) {
                    item {
                        InlineErrorBanner(
                            message = state.errorMessage,
                            onRetry = onRetry
                        )
                    }
                }

                if (state.visibleItems.isEmpty()) {
                    item {
                        EmptyState()
                    }
                } else {
                    items(
                        items = state.visibleItems,
                        key = { it.item.id }
                    ) { itemUi ->
                        ExamItemCard(
                            itemUi = itemUi,
                            readOnlyCache = state.isReadOnlyCache,
                            onToggleAnswer = { onToggleAnswer(itemUi.item.id) },
                            onSelectSingleChoice = { onSelectSingleChoice(itemUi.item.id, it) },
                            onToggleClozeChoice = { onToggleClozeChoice(itemUi.item.id, it) },
                            onSelectMatchingLeft = { onSelectMatchingLeft(itemUi.item.id, it) },
                            onSelectMatchingRight = { onSelectMatchingRight(itemUi.item.id, it) },
                            onTranslationChange = { onTranslationChange(itemUi.item.id, it) },
                            onToggleFavorite = { onToggleFavorite(itemUi.item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    title: String,
    onBack: () -> Unit
) {
    Surface(shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(text = "\u8fd4\u56de")
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(64.dp))
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "\u6b63\u5728\u52a0\u8f7d\u771f\u9898...")
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text(text = "\u91cd\u8bd5")
            }
        }
    }
}

@Composable
private fun SummaryCard(state: WordExamPracticeUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u5355\u8bcd\uff1a${state.wordText.ifBlank { "-" }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SummaryLine(
                "\u5168\u90e8\u9898\u76ee",
                state.totalCount.toString(),
                "\u53ef\u5224\u5206",
                state.objectiveCount.toString()
            )
            SummaryLine(
                "\u6536\u85cf",
                state.favoriteCount.toString(),
                "\u9519\u9898",
                state.wrongCount.toString()
            )
        }
    }
}

@Composable
private fun SummaryLine(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$leftLabel\uff1a$leftValue")
        Text(text = "$rightLabel\uff1a$rightValue")
    }
}

@Composable
private fun ReadOnlyBanner() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD)
        )
    ) {
        Text(
            text = "\u5f53\u524d\u4f7f\u7528\u672c\u5730\u7f13\u5b58\u9884\u89c8\uff0c\u53ef\u6d4f\u89c8\u9898\u76ee\uff0c\u4f46\u4e0d\u652f\u6301\u6536\u85cf\u548c\u7ec3\u4e60\u8bb0\u5f55\u63d0\u4ea4\u3002",
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF7A5C00)
        )
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onRetry) {
                Text(text = "\u91cd\u8bd5")
            }
        }
    }
}

@Composable
private fun FilterSection(
    state: WordExamPracticeUiState,
    onToggleType: (ExamQuestionType) -> Unit,
    onToggleCategory: (ExamCategory) -> Unit,
    onStatusFilterChange: (ExamStatusFilter) -> Unit,
    onShowAllAnswers: () -> Unit,
    onHideAllAnswers: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChipRow(
                title = "\u9898\u578b",
                values = ExamQuestionType.entries,
                isSelected = { it in state.selectedTypes },
                label = ::questionTypeLabel,
                onToggle = onToggleType
            )
            FilterChipRow(
                title = "\u8003\u8bd5",
                values = ExamCategory.entries,
                isSelected = { it in state.selectedCategories },
                label = ::examCategoryLabel,
                onToggle = onToggleCategory
            )
            FilterChipRow(
                title = "\u72b6\u6001",
                values = ExamStatusFilter.entries,
                isSelected = { it == state.statusFilter },
                label = ::statusFilterLabel,
                onToggle = onStatusFilterChange
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onShowAllAnswers
                ) {
                    Text(text = "\u5168\u90e8\u663e\u793a\u7b54\u6848")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onHideAllAnswers
                ) {
                    Text(text = "\u5168\u90e8\u9690\u85cf\u7b54\u6848")
                }
            }
        }
    }
}

@Composable
private fun <T> FilterChipRow(
    title: String,
    values: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onToggle: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = isSelected(value),
                    onClick = { onToggle(value) },
                    label = { Text(text = label(value)) }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card {
        Text(
            text = "\u5f53\u524d\u7b5b\u9009\u6761\u4ef6\u4e0b\u6682\u65e0\u9898\u76ee\u3002",
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExamItemCard(
    itemUi: WordExamPracticeItemUi,
    readOnlyCache: Boolean,
    onToggleAnswer: () -> Unit,
    onSelectSingleChoice: (Int) -> Unit,
    onToggleClozeChoice: (String) -> Unit,
    onSelectMatchingLeft: (Int) -> Unit,
    onSelectMatchingRight: (Int) -> Unit,
    onTranslationChange: (String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val item = itemUi.item
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ItemHeader(
                itemUi = itemUi,
                readOnlyCache = readOnlyCache,
                onToggleFavorite = onToggleFavorite
            )

            val contextText = item.contextText
            if (!contextText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = contextText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = item.contentText,
                style = MaterialTheme.typography.bodyLarge
            )

            when (item.questionType) {
                ExamQuestionType.SINGLE_CHOICE -> SingleChoiceSection(
                    itemUi = itemUi,
                    onSelect = onSelectSingleChoice
                )

                ExamQuestionType.CLOZE -> ClozeSection(
                    itemUi = itemUi,
                    onToggle = onToggleClozeChoice
                )

                ExamQuestionType.MATCHING -> MatchingSection(
                    itemUi = itemUi,
                    onSelectLeft = onSelectMatchingLeft,
                    onSelectRight = onSelectMatchingRight
                )

                ExamQuestionType.TRANSLATION -> TranslationSection(
                    value = itemUi.translationInput,
                    onValueChange = onTranslationChange
                )

                ExamQuestionType.PASSAGE -> PassageSection()
            }

            if (item.questionType != ExamQuestionType.PASSAGE) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onToggleAnswer
                ) {
                    Text(
                        text = if (itemUi.showAnswer) {
                            "\u9690\u85cf\u7b54\u6848"
                        } else {
                            "\u67e5\u770b\u7b54\u6848"
                        }
                    )
                }
            }

            if (itemUi.showAnswer && item.questionType != ExamQuestionType.PASSAGE) {
                AnswerBlock(itemUi = itemUi)
            }
        }
    }
}

@Composable
private fun ItemHeader(
    itemUi: WordExamPracticeItemUi,
    readOnlyCache: Boolean,
    onToggleFavorite: () -> Unit
) {
    val item = itemUi.item
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${questionTypeLabel(item.questionType)}  ${examCategoryLabel(item.examCategory)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = difficultyLabel(item.difficultyLevel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (item.paperName.isNotBlank()) {
            Text(
                text = "\u8bd5\u5377\uff1a${item.paperName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusTag(text = if (item.state?.favorite == true) "\u5df2\u6536\u85cf" else "\u672a\u6536\u85cf")
            if (item.state?.wrongBook == true) {
                StatusTag(
                    text = "\u9519\u9898",
                    containerColor = Color(0xFFFFE5E5),
                    contentColor = Color(0xFFC62828)
                )
            }
            item.state?.lastResult?.let { result ->
                StatusTag(
                    text = lastResultLabel(result),
                    containerColor = lastResultColor(result).first,
                    contentColor = lastResultColor(result).second
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                enabled = !readOnlyCache,
                onClick = onToggleFavorite
            ) {
                Text(
                    text = if (item.state?.favorite == true) {
                        "\u53d6\u6d88\u6536\u85cf"
                    } else {
                        "\u6536\u85cf\u9898\u76ee"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusTag(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SingleChoiceSection(
    itemUi: WordExamPracticeItemUi,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemUi.item.options.forEachIndexed { index, option ->
            val selected = itemUi.selectedOptionIndex == index
            ChoiceRow(
                text = "${optionPrefix(index)}. $option",
                selected = selected,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun ClozeSection(
    itemUi: WordExamPracticeItemUi,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (itemUi.selectedClozeAnswers.isNotEmpty()) {
            Text(
                text = "\u5df2\u9009\uff1a${itemUi.selectedClozeAnswers.joinToString("\u3001")}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemUi.item.options.forEach { option ->
                FilterChip(
                    selected = option in itemUi.selectedClozeAnswers,
                    onClick = { onToggle(option) },
                    label = { Text(text = option) }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun MatchingSection(
    itemUi: WordExamPracticeItemUi,
    onSelectLeft: (Int) -> Unit,
    onSelectRight: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (itemUi.pendingLeftIndex == null) {
                "\u5148\u70b9\u51fb\u5de6\u4fa7\u5185\u5bb9\uff0c\u518d\u9009\u62e9\u53f3\u4fa7\u5339\u914d\u9879\u3002"
            } else {
                "\u5f53\u524d\u5df2\u9009\u4e2d\u5de6\u4fa7 ${itemUi.pendingLeftIndex + 1}\uff0c\u8bf7\u9009\u62e9\u53f3\u4fa7\u5bf9\u5e94\u9879\u3002"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\u5de6\u4fa7",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                itemUi.item.leftItems.forEachIndexed { index, text ->
                    val selected = itemUi.pendingLeftIndex == index
                    ChoiceRow(
                        text = "${index + 1}. $text",
                        selected = selected,
                        onClick = { onSelectLeft(index) }
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\u53f3\u4fa7",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                itemUi.item.rightItems.forEachIndexed { index, text ->
                    val matchedLeft = itemUi.matchingPairs.entries.firstOrNull { it.value == index }?.key
                    val selected = matchedLeft != null
                    ChoiceRow(
                        text = if (matchedLeft == null) {
                            "${optionPrefix(index)}. $text"
                        } else {
                            "${optionPrefix(index)}. $text   (${matchedLeft + 1})"
                        },
                        selected = selected,
                        onClick = { onSelectRight(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TranslationSection(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = "\u8f93\u5165\u4f60\u7684\u7ffb\u8bd1") },
        minLines = 3,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Text
        )
    )
}

@Composable
private fun PassageSection() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "\u8fd9\u662f\u9605\u8bfb\u6750\u6599\uff0c\u4f5c\u4e3a\u4e0a\u4e0b\u6587\u5c55\u793a\uff0c\u4e0d\u5355\u72ec\u4f5c\u7b54\u3002",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AnswerBlock(itemUi: WordExamPracticeItemUi) {
    val item = itemUi.item
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u53c2\u8003\u7b54\u6848",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildAnswerText(item),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!item.analysisText.isNullOrBlank()) {
                Text(
                    text = "\u89e3\u6790\uff1a${item.analysisText}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun buildAnswerText(item: com.chen.memorizewords.domain.model.practice.WordExamItem): String {
    return when (item.questionType) {
        ExamQuestionType.SINGLE_CHOICE -> item.answerIndexes.joinToString("\u3001") { index ->
            optionPrefix(index)
        }

        ExamQuestionType.CLOZE,
        ExamQuestionType.TRANSLATION -> item.answers.joinToString("\u3001").ifBlank { "-" }

        ExamQuestionType.MATCHING -> {
            item.answerIndexes.mapIndexed { leftIndex, rightIndex ->
                "${leftIndex + 1}-${optionPrefix(rightIndex)}"
            }.joinToString("\u3001")
        }

        ExamQuestionType.PASSAGE -> "-"
    }
}

private fun questionTypeLabel(type: ExamQuestionType): String {
    return when (type) {
        ExamQuestionType.SINGLE_CHOICE -> "\u5355\u9879\u9009\u62e9"
        ExamQuestionType.CLOZE -> "\u9009\u8bcd\u586b\u7a7a"
        ExamQuestionType.MATCHING -> "\u5339\u914d\u9898"
        ExamQuestionType.PASSAGE -> "\u77ed\u6587\u7247\u6bb5"
        ExamQuestionType.TRANSLATION -> "\u7ffb\u8bd1\u9898"
    }
}

private fun examCategoryLabel(category: ExamCategory): String {
    return when (category) {
        ExamCategory.CET4 -> "\u56db\u7ea7"
        ExamCategory.CET6 -> "\u516d\u7ea7"
        ExamCategory.POSTGRADUATE -> "\u8003\u7814"
    }
}

private fun statusFilterLabel(filter: ExamStatusFilter): String {
    return when (filter) {
        ExamStatusFilter.ALL -> "\u5168\u90e8"
        ExamStatusFilter.FAVORITE -> "\u6536\u85cf"
        ExamStatusFilter.WRONG -> "\u9519\u9898"
    }
}

private fun difficultyLabel(level: Int): String {
    val normalized = level.coerceIn(1, 5)
    return "\u96be\u5ea6 " + "*".repeat(normalized)
}

private fun optionPrefix(index: Int): String {
    return ('A' + index).toString()
}

private fun lastResultLabel(result: ExamItemLastResult): String {
    return when (result) {
        ExamItemLastResult.CORRECT -> "\u4e0a\u6b21\u7b54\u5bf9"
        ExamItemLastResult.WRONG -> "\u4e0a\u6b21\u7b54\u9519"
        ExamItemLastResult.UNGRADED -> "\u672a\u5224\u5206"
    }
}

private fun lastResultColor(result: ExamItemLastResult): Pair<Color, Color> {
    return when (result) {
        ExamItemLastResult.CORRECT -> Color(0xFFE6F4EA) to Color(0xFF2E7D32)
        ExamItemLastResult.WRONG -> Color(0xFFFFE5E5) to Color(0xFFC62828)
        ExamItemLastResult.UNGRADED -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
    }
}
