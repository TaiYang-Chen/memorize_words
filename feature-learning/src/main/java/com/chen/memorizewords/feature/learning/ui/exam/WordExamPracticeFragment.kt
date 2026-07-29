package com.chen.memorizewords.feature.learning.ui.exam

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.chen.memorizewords.core.ui.R as CoreUiR
import com.chen.memorizewords.domain.practice.model.ExamCategory
import com.chen.memorizewords.domain.practice.model.ExamQuestionType
import com.chen.memorizewords.domain.practice.model.WordExamItem
import com.chen.memorizewords.feature.learning.R
import dagger.hilt.android.AndroidEntryPoint

private val ExamGreen = Color(0xFF10B981)
private val ExamGreenPressed = Color(0xFF0EA575)
private val ExamNavy = Color(0xFF172337)
private val ExamMuted = Color(0xFF627083)
private val ExamCanvas = Color(0xFFF7F9FB)
private val ExamBorder = Color(0xFFDDE3E8)
private val ExamSoftGreen = Color(0xFFE9FAF4)
private val ExamOrange = Color(0xFFF97316)

private val ExamColorScheme = lightColorScheme(
    primary = ExamGreen,
    onPrimary = Color.White,
    primaryContainer = ExamSoftGreen,
    onPrimaryContainer = ExamNavy,
    background = ExamCanvas,
    onBackground = ExamNavy,
    surface = Color.White,
    onSurface = ExamNavy,
    surfaceVariant = Color(0xFFF1F4F6),
    onSurfaceVariant = ExamMuted,
    outline = ExamBorder,
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFFEDEC),
    onErrorContainer = Color(0xFF7A271A)
)

@AndroidEntryPoint
class WordExamPracticeFragment : Fragment() {

    private val viewModel: WordExamPracticeViewModel by viewModels()
    private var previousStatusBarColor: Int? = null
    private var previousLightStatusBars: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MaterialTheme(colorScheme = ExamColorScheme) {
                    WordExamPracticeScreen(
                        state = uiState,
                        onBack = { findNavController().navigateUp() },
                        onRetry = viewModel::load,
                        onToggleType = viewModel::toggleType,
                        onClearTypeFilters = viewModel::clearTypeFilters,
                        onCategorySelected = viewModel::setCategory,
                        onShowVisibleAnswers = viewModel::showVisibleAnswers,
                        onHideVisibleAnswers = viewModel::hideVisibleAnswers,
                        onToggleAnswer = viewModel::toggleAnswer
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val window = requireActivity().window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (previousStatusBarColor == null) {
            previousStatusBarColor = window.statusBarColor
            previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        }
        window.statusBarColor = AndroidColor.WHITE
        insetsController.isAppearanceLightStatusBars = true
    }

    override fun onStop() {
        val window = activity?.window
        if (window != null) {
            previousStatusBarColor?.let { window.statusBarColor = it }
            previousLightStatusBars?.let {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = it
            }
        }
        previousStatusBarColor = null
        previousLightStatusBars = null
        super.onStop()
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
    onToggleAnswer: (Long) -> Unit
) {
    val answerableItems = state.visibleItems.filterNot {
        it.item.questionType == ExamQuestionType.PASSAGE
    }
    val allVisibleAnswersShown = answerableItems.isNotEmpty() && answerableItems.all { it.showAnswer }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ExamCanvas
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExamHeaderBar(
                title = if (state.wordText.isBlank()) {
                    "\u771f\u9898"
                } else {
                    "${state.wordText} \u771f\u9898"
                },
                allAnswersShown = allVisibleAnswersShown,
                answerActionEnabled = answerableItems.isNotEmpty(),
                onBack = onBack,
                onToggleAllAnswers = {
                    if (allVisibleAnswersShown) onHideVisibleAnswers() else onShowVisibleAnswers()
                }
            )

            when {
                state.isLoading && state.items.isEmpty() -> LoadingState()
                state.errorMessage != null && state.items.isEmpty() -> ErrorState(
                    message = state.errorMessage,
                    onRetry = onRetry
                )
                else -> ExamContent(
                    state = state,
                    onRetry = onRetry,
                    onToggleType = onToggleType,
                    onClearTypeFilters = onClearTypeFilters,
                    onCategorySelected = onCategorySelected,
                    onToggleAnswer = onToggleAnswer
                )
            }
        }
    }
}

@Composable
private fun ExamHeaderBar(
    title: String,
    allAnswersShown: Boolean,
    answerActionEnabled: Boolean,
    onBack: () -> Unit,
    onToggleAllAnswers: () -> Unit
) {
    val pageMargin = dimensionResource(id = CoreUiR.dimen.page_margin)
    val titleMargin = dimensionResource(id = CoreUiR.dimen.title_margin)
    val iconSize = dimensionResource(id = CoreUiR.dimen.title_icon_size)
    val titleTop = pageMargin + titleMargin
    val titleHorizontalInset = 48.dp
    val headerBottomPadding = dimensionResource(id = CoreUiR.dimen.core_ui_dp_10)
    val headerHeight = titleTop + iconSize + headerBottomPadding

    Surface(color = Color.White) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = pageMargin,
                        y = titleTop
                    )
                    .size(iconSize)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "\u8fd4\u56de",
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    painter = painterResource(id = CoreUiR.drawable.ic_arrow_back),
                    contentDescription = "\u8fd4\u56de",
                    tint = ExamNavy
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = titleTop)
                    .fillMaxWidth()
                    .height(iconSize)
                    .padding(horizontal = pageMargin + titleHorizontalInset),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = ExamNavy,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = -pageMargin,
                        y = titleTop
                    )
                    .size(iconSize)
                    .clickable(
                        enabled = answerActionEnabled,
                        role = Role.Button,
                        onClickLabel = if (allAnswersShown) {
                            "\u4e00\u952e\u9690\u85cf\u7b54\u6848"
                        } else {
                            "\u4e00\u952e\u663e\u793a\u7b54\u6848"
                        },
                        onClick = onToggleAllAnswers
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    painter = painterResource(
                        id = if (allAnswersShown) {
                            R.drawable.feature_learning_ic_visibility_off
                        } else {
                            R.drawable.feature_learning_ic_visibility
                        }
                    ),
                    contentDescription = if (allAnswersShown) {
                        "\u4e00\u952e\u9690\u85cf\u7b54\u6848"
                    } else {
                        "\u4e00\u952e\u663e\u793a\u7b54\u6848"
                    },
                    tint = if (answerActionEnabled) ExamNavy else ExamMuted.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun ExamContent(
    state: WordExamPracticeUiState,
    onRetry: () -> Unit,
    onToggleType: (ExamQuestionType) -> Unit,
    onClearTypeFilters: () -> Unit,
    onCategorySelected: (ExamCategory?) -> Unit,
    onToggleAnswer: (Long) -> Unit
) {
    val horizontalPadding = dimensionResource(
        id = R.dimen.feature_learning_exam_page_horizontal_padding
    )
    val verticalPadding = dimensionResource(
        id = R.dimen.feature_learning_exam_page_vertical_padding
    )
    val sectionSpacing = dimensionResource(
        id = R.dimen.feature_learning_exam_section_spacing
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            top = verticalPadding,
            end = horizontalPadding,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        item {
            FilterSection(
                state = state,
                onToggleType = onToggleType,
                onClearTypeFilters = onClearTypeFilters,
                onCategorySelected = onCategorySelected
            )
        }

        state.errorMessage?.let { message ->
            item {
                InlineErrorBanner(message = message, onRetry = onRetry)
            }
        }

        if (state.visibleItems.isEmpty()) {
            item { EmptyState() }
        } else {
            items(
                items = state.visibleItems,
                key = { it.item.id }
            ) { itemUi ->
                ExamItemCard(
                    itemUi = itemUi,
                    onToggleAnswer = { onToggleAnswer(itemUi.item.id) }
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    state: WordExamPracticeUiState,
    onToggleType: (ExamQuestionType) -> Unit,
    onClearTypeFilters: () -> Unit,
    onCategorySelected: (ExamCategory?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryFilterRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = onCategorySelected
        )
        QuestionTypeFilterRow(
            selectedTypes = state.selectedTypes,
            onClearTypeFilters = onClearTypeFilters,
            onToggleType = onToggleType
        )
        Text(
            text = "\u5171 ${state.totalCount} \u9053\u771f\u9898",
            modifier = Modifier.padding(top = 4.dp),
            color = ExamNavy,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: ExamCategory?,
    onCategorySelected: (ExamCategory?) -> Unit
) {
    val categories = listOf(
        null to "\u5168\u90e8",
        ExamCategory.CET4 to "\u56db\u7ea7",
        ExamCategory.CET6 to "\u516d\u7ea7",
        ExamCategory.POSTGRADUATE to "\u8003\u7814"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (category, label) ->
            FilterButton(
                modifier = Modifier.weight(1f),
                text = label,
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun QuestionTypeFilterRow(
    selectedTypes: Set<ExamQuestionType>,
    onClearTypeFilters: () -> Unit,
    onToggleType: (ExamQuestionType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterButton(
            modifier = Modifier.weight(1f),
            text = "\u5168\u90e8",
            selected = selectedTypes.isEmpty(),
            onClick = onClearTypeFilters,
            compact = true
        )
        ExamQuestionType.entries.forEach { type ->
            FilterButton(
                modifier = Modifier.weight(1f),
                text = questionTypeLabel(type),
                selected = type in selectedTypes,
                onClick = { onToggleType(type) },
                compact = true
            )
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val widthModifier = if (compact) modifier else modifier.widthIn(min = 58.dp)
    Surface(
        modifier = widthModifier.height(if (compact) 32.dp else 36.dp),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (selected) ExamGreen else Color.White,
        border = BorderStroke(1.dp, if (selected) ExamGreen else ExamBorder)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = if (compact) 1.dp else 6.dp),
                color = if (selected) Color.White else ExamNavy,
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ExamItemCard(
    itemUi: WordExamPracticeItemUi,
    onToggleAnswer: () -> Unit
) {
    val item = itemUi.item
    val cardPadding = dimensionResource(id = R.dimen.feature_learning_exam_card_inner_padding)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ExamBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            ItemMetadata(item = item)
            Spacer(modifier = Modifier.height(8.dp))
            DividerLine()
            Spacer(modifier = Modifier.height(10.dp))

            item.contextText?.takeIf { it.isNotBlank() }?.let { context ->
                Text(
                    text = context,
                    color = ExamNavy,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (item.contentText.isNotBlank()) {
                Text(
                    text = item.contentText,
                    color = ExamNavy,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            when (item.questionType) {
                ExamQuestionType.SINGLE_CHOICE,
                ExamQuestionType.CLOZE -> {
                    if (item.options.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        StaticOptions(
                            item = item,
                            revealCorrectChoice = itemUi.showAnswer &&
                                item.questionType == ExamQuestionType.SINGLE_CHOICE
                        )
                    }
                }

                ExamQuestionType.MATCHING -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    MatchingContent(item = item)
                }

                ExamQuestionType.PASSAGE,
                ExamQuestionType.TRANSLATION -> Unit
            }

            if (item.questionType != ExamQuestionType.PASSAGE) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clickable(
                                role = Role.Button,
                                onClick = onToggleAnswer
                            )
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (itemUi.showAnswer) {
                                "\u9690\u85cf\u7b54\u6848"
                            } else {
                                "\u663e\u793a\u7b54\u6848"
                            },
                            color = ExamGreenPressed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (itemUi.showAnswer) {
                    answerContentFor(item)?.let { answer ->
                        AnswerBlock(answer = answer)
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemMetadata(item: WordExamItem) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "${questionTypeLabel(item.questionType)} \u00b7 ${examCategoryLabel(item.examCategory)}",
                modifier = Modifier.weight(1f),
                color = ExamNavy,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\u96be\u5ea6 ${item.difficultyLevel.coerceIn(1, 5)}",
                color = ExamOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (item.paperName.isNotBlank()) {
            Text(
                text = "\u8bd5\u5377\uff1a${item.paperName}",
                color = ExamMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun StaticOptions(
    item: WordExamItem,
    revealCorrectChoice: Boolean
) {
    val optionPadding = dimensionResource(id = R.dimen.feature_learning_exam_choice_padding)
    Column(modifier = Modifier.fillMaxWidth()) {
        item.options.forEachIndexed { index, option ->
            val isCorrect = revealCorrectChoice && index in item.answerIndexes
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = if (isCorrect) ExamSoftGreen else Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .padding(horizontal = optionPadding, vertical = 9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${optionPrefix(index) ?: index + 1}.",
                        modifier = Modifier.width(28.dp),
                        color = ExamNavy,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = option,
                        modifier = Modifier.weight(1f),
                        color = ExamNavy,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            if (index != item.options.lastIndex) DividerLine()
        }
    }
}

@Composable
private fun MatchingContent(item: WordExamItem) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MatchingColumn(
                    heading = "\u9898\u76ee",
                    values = item.leftItems,
                    prefix = { index -> "${index + 1}." }
                )
                MatchingColumn(
                    heading = "\u9009\u9879",
                    values = item.rightItems,
                    prefix = { index -> "${optionPrefix(index) ?: index + 1}." }
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MatchingColumn(
                    modifier = Modifier.weight(1f),
                    heading = "\u9898\u76ee",
                    values = item.leftItems,
                    prefix = { index -> "${index + 1}." }
                )
                MatchingColumn(
                    modifier = Modifier.weight(1f),
                    heading = "\u9009\u9879",
                    values = item.rightItems,
                    prefix = { index -> "${optionPrefix(index) ?: index + 1}." }
                )
            }
        }
    }
}

@Composable
private fun MatchingColumn(
    heading: String,
    values: List<String>,
    prefix: (Int) -> String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = heading,
            color = ExamMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = prefix(index),
                    modifier = Modifier.width(24.dp),
                    color = ExamNavy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    color = ExamNavy,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun AnswerBlock(answer: ExamAnswerContent) {
    val answerGap = dimensionResource(id = R.dimen.feature_learning_exam_answer_gap)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(ExamSoftGreen, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    color = ExamGreen,
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                )
        )
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(answerGap)
        ) {
            Text(
                text = answer.heading,
                color = ExamGreenPressed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = answer.body,
                color = ExamNavy,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ExamBorder.copy(alpha = 0.78f))
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ExamGreen)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "\u6b63\u5728\u52a0\u8f7d\u771f\u9898...",
                color = ExamMuted
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = ExamGreen)
            ) {
                Text(text = "\u91cd\u8bd5")
            }
        }
    }
}

@Composable
private fun InlineErrorBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 14.sp
            )
            TextButton(onClick = onRetry) {
                Text(text = "\u91cd\u8bd5")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ExamBorder)
    ) {
        Text(
            text = "\u5f53\u524d\u7b5b\u9009\u6761\u4ef6\u4e0b\u6682\u65e0\u771f\u9898",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp),
            color = ExamMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
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
