package com.chen.memorizewords.feature.learning.ui.exam

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamItemLastResult
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.feature.learning.R
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
                        onClearTypeFilters = viewModel::clearTypeFilters,
                        onCategorySelected = viewModel::setCategory,
                        onShowVisibleAnswers = viewModel::showVisibleAnswers,
                        onHideVisibleAnswers = viewModel::hideVisibleAnswers,
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
    onClearTypeFilters: () -> Unit,
    onCategorySelected: (ExamCategory?) -> Unit,
    onShowVisibleAnswers: () -> Unit,
    onHideVisibleAnswers: () -> Unit,
    onToggleAnswer: (Long) -> Unit,
    onSelectSingleChoice: (Long, Int) -> Unit,
    onToggleClozeChoice: (Long, String) -> Unit,
    onSelectMatchingLeft: (Long, Int) -> Unit,
    onSelectMatchingRight: (Long, Int) -> Unit,
    onTranslationChange: (Long, String) -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    val dimensions = rememberExamDimensions()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                title = if (state.wordText.isBlank()) "\u771f\u9898\u7ec3\u4e60" else "${state.wordText} \u771f\u9898",
                onBack = onBack,
                onShowVisibleAnswers = onShowVisibleAnswers,
                onHideVisibleAnswers = onHideVisibleAnswers,
                dimensions = dimensions
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
                contentPadding = PaddingValues(
                    horizontal = dimensions.pageHorizontalPadding,
                    vertical = dimensions.pageVerticalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing)
            ) {
                item {
                    TopFilterSection(
                        state = state,
                        onToggleType = onToggleType,
                        onClearTypeFilters = onClearTypeFilters,
                        onCategorySelected = onCategorySelected,
                        dimensions = dimensions
                    )
                }

                if (state.isReadOnlyCache) {
                    item {
                        ReadOnlyBanner(dimensions = dimensions)
                    }
                }

                if (state.errorMessage != null) {
                    item {
                        InlineErrorBanner(
                            message = state.errorMessage,
                            onRetry = onRetry,
                            dimensions = dimensions
                        )
                    }
                }

                if (state.visibleItems.isEmpty()) {
                    item {
                        EmptyState(dimensions = dimensions)
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
                            onToggleFavorite = { onToggleFavorite(itemUi.item.id) },
                            dimensions = dimensions
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
    onBack: () -> Unit,
    onShowVisibleAnswers: () -> Unit,
    onHideVisibleAnswers: () -> Unit,
    dimensions: ExamDimensions
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(shadowElevation = 2.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensions.headerPaddingHorizontal,
                    vertical = dimensions.headerPaddingVertical
                )
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.headerTitleSideInset),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                fontSize = dimensions.headerTitleSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    modifier = Modifier
                        .width(dimensions.headerTitleSideInset)
                        .height(dimensions.headerIconTouch),
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "\u8fd4\u56de",
                        fontSize = dimensions.pillTextSize
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.width(dimensions.headerTitleSideInset),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    IconButton(
                        modifier = Modifier.size(dimensions.headerIconTouch),
                        onClick = { menuExpanded = true }
                    ) {
                        Icon(
                            modifier = Modifier.size(dimensions.headerIconSize),
                            painter = painterResource(id = R.drawable.feature_learning_ic_more_vert),
                            contentDescription = "更多操作",
                            tint = Color(0xFF243647)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "\u663e\u793a\u5168\u90e8\u7b54\u6848") },
                            onClick = {
                                menuExpanded = false
                                onShowVisibleAnswers()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = "\u9690\u85cf\u5168\u90e8\u7b54\u6848") },
                            onClick = {
                                menuExpanded = false
                                onHideVisibleAnswers()
                            }
                        )
                    }
                }
            }
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
private fun TopFilterSection(
    state: WordExamPracticeUiState,
    onToggleType: (ExamQuestionType) -> Unit,
    onClearTypeFilters: () -> Unit,
    onCategorySelected: (ExamCategory?) -> Unit,
    dimensions: ExamDimensions
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.filterBlockSpacing)) {
        CategoryTabRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = onCategorySelected,
            dimensions = dimensions
        )
        QuestionTypePillRow(
            selectedTypes = state.selectedTypes,
            onClearTypeFilters = onClearTypeFilters,
            onToggleType = onToggleType,
            dimensions = dimensions
        )
    }
}

@Composable
private fun CategoryTabRow(
    selectedCategory: ExamCategory?,
    onCategorySelected: (ExamCategory?) -> Unit,
    dimensions: ExamDimensions
) {
    val tabs = listOf(
        null to "\u5168\u90e8",
        ExamCategory.CET4 to "\u56db\u7ea7",
        ExamCategory.CET6 to "\u516d\u7ea7",
        ExamCategory.POSTGRADUATE to "\u8003\u7814"
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensions.categoryGap)
    ) {
        tabs.forEach { (category, label) ->
            val selected = selectedCategory == category
            Text(
                text = label,
                modifier = Modifier.clickable { onCategorySelected(category) },
                color = if (selected) Color(0xFF243647) else Color(0xFF9AA4AF),
                fontSize = dimensions.categoryTextSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun QuestionTypePillRow(
    selectedTypes: Set<ExamQuestionType>,
    onClearTypeFilters: () -> Unit,
    onToggleType: (ExamQuestionType) -> Unit,
    dimensions: ExamDimensions
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensions.typeGap)
    ) {
        FilterPill(
            text = "\u5168\u90e8",
            selected = selectedTypes.isEmpty(),
            onClick = onClearTypeFilters,
            dimensions = dimensions
        )
        ExamQuestionType.entries.forEach { type ->
            FilterPill(
                text = questionTypeLabel(type),
                selected = type in selectedTypes,
                onClick = { onToggleType(type) },
                dimensions = dimensions
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dimensions: ExamDimensions
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color(0xFFDCE4EC) else Color(0xFFF1F4F7)
    ) {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = dimensions.pillHorizontalPadding,
                    vertical = dimensions.pillVerticalPadding
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = if (selected) Color(0xFF243647) else Color(0xFF526170),
                fontSize = dimensions.pillTextSize,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ReadOnlyBanner(dimensions: ExamDimensions) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3CD)
        )
    ) {
        Text(
            text = "\u5f53\u524d\u4f7f\u7528\u672c\u5730\u7f13\u5b58\u9884\u89c8\uff0c\u53ef\u6d4f\u89c8\u9898\u76ee\uff0c\u4f46\u4e0d\u652f\u6301\u6536\u85cf\u548c\u7ec3\u4e60\u8bb0\u5f55\u63d0\u4ea4\u3002",
            modifier = Modifier.padding(dimensions.bannerPadding),
            color = Color(0xFF7A5C00)
        )
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    onRetry: () -> Unit,
    dimensions: ExamDimensions
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.bannerPadding),
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
private fun EmptyState(dimensions: ExamDimensions) {
    Card {
        Text(
            text = "\u5f53\u524d\u7b5b\u9009\u6761\u4ef6\u4e0b\u6682\u65e0\u9898\u76ee\u3002",
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.emptyPadding),
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
    onToggleFavorite: () -> Unit,
    dimensions: ExamDimensions
) {
    val item = itemUi.item
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.cardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(dimensions.cardSectionGap)
        ) {
            ItemHeader(
                itemUi = itemUi,
                readOnlyCache = readOnlyCache,
                onToggleFavorite = onToggleFavorite,
                dimensions = dimensions
            )

            val contextText = item.contextText
            if (!contextText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(dimensions.blockCorner),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = contextText,
                        modifier = Modifier.padding(dimensions.blockPadding),
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
                    onSelect = onSelectSingleChoice,
                    dimensions = dimensions
                )

                ExamQuestionType.CLOZE -> ClozeSection(
                    itemUi = itemUi,
                    onToggle = onToggleClozeChoice,
                    dimensions = dimensions
                )

                ExamQuestionType.MATCHING -> MatchingSection(
                    itemUi = itemUi,
                    onSelectLeft = onSelectMatchingLeft,
                    onSelectRight = onSelectMatchingRight,
                    dimensions = dimensions
                )

                ExamQuestionType.TRANSLATION -> TranslationSection(
                    value = itemUi.translationInput,
                    onValueChange = onTranslationChange,
                    dimensions = dimensions
                )

                ExamQuestionType.PASSAGE -> PassageSection(dimensions = dimensions)
            }

            if (item.questionType != ExamQuestionType.PASSAGE) {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.headerIconTouch),
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
                AnswerBlock(itemUi = itemUi, dimensions = dimensions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemHeader(
    itemUi: WordExamPracticeItemUi,
    readOnlyCache: Boolean,
    onToggleFavorite: () -> Unit,
    dimensions: ExamDimensions
) {
    val item = itemUi.item
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.answerGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.statusGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${questionTypeLabel(item.questionType)}  ${examCategoryLabel(item.examCategory)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = difficultyLabel(item.difficultyLevel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End
            )
        }
        if (item.paperName.isNotBlank()) {
            Text(
                text = "\u8bd5\u5377\uff1a${item.paperName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.statusGap),
            verticalArrangement = Arrangement.spacedBy(dimensions.answerGap)
        ) {
            StatusTag(
                text = if (item.state?.favorite == true) "\u5df2\u6536\u85cf" else "\u672a\u6536\u85cf",
                dimensions = dimensions
            )
            if (item.state?.wrongBook == true) {
                StatusTag(
                    text = "\u9519\u9898",
                    containerColor = Color(0xFFFFE5E5),
                    contentColor = Color(0xFFC62828),
                    dimensions = dimensions
                )
            }
            item.state?.lastResult?.let { result ->
                StatusTag(
                    text = lastResultLabel(result),
                    containerColor = lastResultColor(result).first,
                    contentColor = lastResultColor(result).second,
                    dimensions = dimensions
                )
            }
            TextButton(
                enabled = !readOnlyCache,
                onClick = onToggleFavorite,
                contentPadding = PaddingValues(
                    horizontal = dimensions.statusHorizontalPadding,
                    vertical = 0.dp
                )
            ) {
                Text(
                    text = if (item.state?.favorite == true) {
                        "\u53d6\u6d88\u6536\u85cf"
                    } else {
                        "\u6536\u85cf\u9898\u76ee"
                    },
                    fontSize = dimensions.statusTextSize
                )
            }
        }
    }
}

@Composable
private fun StatusTag(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    dimensions: ExamDimensions
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(
                horizontal = dimensions.statusHorizontalPadding,
                vertical = dimensions.statusVerticalPadding
            )
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = dimensions.statusTextSize
        )
    }
}

@Composable
private fun SingleChoiceSection(
    itemUi: WordExamPracticeItemUi,
    onSelect: (Int) -> Unit,
    dimensions: ExamDimensions
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.choiceGap)) {
        itemUi.item.options.forEachIndexed { index, option ->
            val selected = itemUi.selectedOptionIndex == index
            ChoiceRow(
                text = "${optionPrefix(index)}. $option",
                selected = selected,
                onClick = { onSelect(index) },
                dimensions = dimensions
            )
        }
    }
}

@Composable
private fun ClozeSection(
    itemUi: WordExamPracticeItemUi,
    onToggle: (String) -> Unit,
    dimensions: ExamDimensions
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.choiceGap)) {
        if (itemUi.selectedClozeAnswers.isNotEmpty()) {
            Text(
                text = "\u5df2\u9009\uff1a${itemUi.selectedClozeAnswers.joinToString("\u3001")}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensions.clozeGap)
        ) {
            itemUi.item.options.forEach { option ->
                FilterChip(
                    selected = option in itemUi.selectedClozeAnswers,
                    onClick = { onToggle(option) },
                    label = { Text(text = option) }
                )
            }
        }
    }
}

@Composable
private fun MatchingSection(
    itemUi: WordExamPracticeItemUi,
    onSelectLeft: (Int) -> Unit,
    onSelectRight: (Int) -> Unit,
    dimensions: ExamDimensions
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensions.choiceGap)) {
        Text(
            text = if (itemUi.pendingLeftIndex == null) {
                "\u5148\u70b9\u51fb\u5de6\u4fa7\u5185\u5bb9\uff0c\u518d\u9009\u62e9\u53f3\u4fa7\u5339\u914d\u9879\u3002"
            } else {
                "\u5f53\u524d\u5df2\u9009\u4e2d\u5de6\u4fa7 ${itemUi.pendingLeftIndex + 1}\uff0c\u8bf7\u9009\u62e9\u53f3\u4fa7\u5bf9\u5e94\u9879\u3002"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        BoxWithConstraints {
            val stackColumns = maxWidth <= dimensions.matchingStackBreakpoint
            if (stackColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.matchingColumnGap)) {
                    MatchingLeftColumn(
                        itemUi = itemUi,
                        onSelectLeft = onSelectLeft,
                        dimensions = dimensions,
                        modifier = Modifier.fillMaxWidth()
                    )
                    MatchingRightColumn(
                        itemUi = itemUi,
                        onSelectRight = onSelectRight,
                        dimensions = dimensions,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.matchingColumnGap)) {
                    MatchingLeftColumn(
                        itemUi = itemUi,
                        onSelectLeft = onSelectLeft,
                        dimensions = dimensions,
                        modifier = Modifier.weight(1f)
                    )
                    MatchingRightColumn(
                        itemUi = itemUi,
                        onSelectRight = onSelectRight,
                        dimensions = dimensions,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchingLeftColumn(
    itemUi: WordExamPracticeItemUi,
    onSelectLeft: (Int) -> Unit,
    dimensions: ExamDimensions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensions.choiceGap)
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
                onClick = { onSelectLeft(index) },
                dimensions = dimensions
            )
        }
    }
}

@Composable
private fun MatchingRightColumn(
    itemUi: WordExamPracticeItemUi,
    onSelectRight: (Int) -> Unit,
    dimensions: ExamDimensions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensions.choiceGap)
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
                onClick = { onSelectRight(index) },
                dimensions = dimensions
            )
        }
    }
}

@Composable
private fun TranslationSection(
    value: String,
    onValueChange: (String) -> Unit,
    dimensions: ExamDimensions
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = "\u8f93\u5165\u4f60\u7684\u7ffb\u8bd1") },
        minLines = dimensions.translationMinLines,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Text
        )
    )
}

@Composable
private fun PassageSection(dimensions: ExamDimensions) {
    Surface(
        shape = RoundedCornerShape(dimensions.blockCorner),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "\u8fd9\u662f\u9605\u8bfb\u6750\u6599\uff0c\u4f5c\u4e3a\u4e0a\u4e0b\u6587\u5c55\u793a\uff0c\u4e0d\u5355\u72ec\u4f5c\u7b54\u3002",
            modifier = Modifier.padding(dimensions.blockPadding),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ChoiceRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dimensions: ExamDimensions
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
            .border(1.dp, borderColor, RoundedCornerShape(dimensions.blockCorner))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dimensions.blockCorner),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(dimensions.choicePadding),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AnswerBlock(
    itemUi: WordExamPracticeItemUi,
    dimensions: ExamDimensions
) {
    val item = itemUi.item
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.cardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(dimensions.answerGap)
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

@Composable
private fun rememberExamDimensions(): ExamDimensions {
    return ExamDimensions(
        pageHorizontalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_page_horizontal_padding),
        pageVerticalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_page_vertical_padding),
        sectionSpacing = dimensionResourceDp(R.dimen.feature_learning_exam_section_spacing),
        headerPaddingHorizontal = dimensionResourceDp(R.dimen.feature_learning_exam_header_padding_horizontal),
        headerPaddingVertical = dimensionResourceDp(R.dimen.feature_learning_exam_header_padding_vertical),
        headerTitleSideInset = dimensionResourceDp(R.dimen.feature_learning_exam_header_title_side_inset),
        headerTitleSize = dimensionResourceSp(R.dimen.feature_learning_exam_header_title_size),
        headerIconTouch = dimensionResourceDp(R.dimen.feature_learning_exam_header_icon_touch),
        headerIconSize = dimensionResourceDp(R.dimen.feature_learning_exam_header_icon_size),
        filterBlockSpacing = dimensionResourceDp(R.dimen.feature_learning_exam_filter_block_spacing),
        categoryGap = dimensionResourceDp(R.dimen.feature_learning_exam_category_gap),
        categoryTextSize = dimensionResourceSp(R.dimen.feature_learning_exam_category_text_size),
        typeGap = dimensionResourceDp(R.dimen.feature_learning_exam_type_gap),
        pillHorizontalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_pill_horizontal_padding),
        pillVerticalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_pill_vertical_padding),
        pillTextSize = dimensionResourceSp(R.dimen.feature_learning_exam_pill_text_size),
        cardInnerPadding = dimensionResourceDp(R.dimen.feature_learning_exam_card_inner_padding),
        cardSectionGap = dimensionResourceDp(R.dimen.feature_learning_exam_card_section_gap),
        blockPadding = dimensionResourceDp(R.dimen.feature_learning_exam_block_padding),
        blockCorner = dimensionResourceDp(R.dimen.feature_learning_exam_block_corner),
        bannerPadding = dimensionResourceDp(R.dimen.feature_learning_exam_banner_padding),
        emptyPadding = dimensionResourceDp(R.dimen.feature_learning_exam_empty_padding),
        statusGap = dimensionResourceDp(R.dimen.feature_learning_exam_status_gap),
        statusHorizontalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_status_horizontal_padding),
        statusVerticalPadding = dimensionResourceDp(R.dimen.feature_learning_exam_status_vertical_padding),
        statusTextSize = dimensionResourceSp(R.dimen.feature_learning_exam_status_text_size),
        choicePadding = dimensionResourceDp(R.dimen.feature_learning_exam_choice_padding),
        choiceGap = dimensionResourceDp(R.dimen.feature_learning_exam_choice_gap),
        clozeGap = dimensionResourceDp(R.dimen.feature_learning_exam_cloze_gap),
        matchingColumnGap = dimensionResourceDp(R.dimen.feature_learning_exam_matching_column_gap),
        matchingStackBreakpoint = dimensionResourceDp(R.dimen.feature_learning_exam_matching_stack_breakpoint),
        translationMinLines = androidx.compose.ui.res.integerResource(
            id = R.integer.feature_learning_exam_translation_min_lines
        ),
        answerGap = dimensionResourceDp(R.dimen.feature_learning_exam_answer_gap)
    )
}

@Composable
private fun dimensionResourceDp(@DimenRes id: Int): Dp {
    return androidx.compose.ui.res.dimensionResource(id = id)
}

@Composable
private fun dimensionResourceSp(@DimenRes id: Int): TextUnit {
    val density = LocalDensity.current
    val resources = LocalResources.current
    return with(density) { resources.getDimension(id).toSp() }
}

private data class ExamDimensions(
    val pageHorizontalPadding: Dp,
    val pageVerticalPadding: Dp,
    val sectionSpacing: Dp,
    val headerPaddingHorizontal: Dp,
    val headerPaddingVertical: Dp,
    val headerTitleSideInset: Dp,
    val headerTitleSize: TextUnit,
    val headerIconTouch: Dp,
    val headerIconSize: Dp,
    val filterBlockSpacing: Dp,
    val categoryGap: Dp,
    val categoryTextSize: TextUnit,
    val typeGap: Dp,
    val pillHorizontalPadding: Dp,
    val pillVerticalPadding: Dp,
    val pillTextSize: TextUnit,
    val cardInnerPadding: Dp,
    val cardSectionGap: Dp,
    val blockPadding: Dp,
    val blockCorner: Dp,
    val bannerPadding: Dp,
    val emptyPadding: Dp,
    val statusGap: Dp,
    val statusHorizontalPadding: Dp,
    val statusVerticalPadding: Dp,
    val statusTextSize: TextUnit,
    val choicePadding: Dp,
    val choiceGap: Dp,
    val clozeGap: Dp,
    val matchingColumnGap: Dp,
    val matchingStackBreakpoint: Dp,
    val translationMinLines: Int,
    val answerGap: Dp
)

private fun buildAnswerText(item: com.chen.memorizewords.domain.practice.model.WordExamItem): String {
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
        ExamQuestionType.SINGLE_CHOICE -> "\u9009\u62e9\u9898"
        ExamQuestionType.CLOZE -> "\u586b\u7a7a\u9898"
        ExamQuestionType.MATCHING -> "\u5339\u914d\u9898"
        ExamQuestionType.PASSAGE -> "\u77ed\u6587"
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
