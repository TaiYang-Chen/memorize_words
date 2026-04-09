package com.chen.memorizewords.feature.learning.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chen.memorizewords.domain.model.learning.LearningTestMode
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.ui.learning.LearningViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class WordLearningTestFragment : Fragment() {

    private val parentViewModel: LearningViewModel by viewModels({ requireParentFragment() })
    private val viewModel: WordLearningTestViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WordLearningTest(
                    uiState = uiState,
                    onOptionSelected = { index ->
                        val option = viewModel.selectOption(index) ?: return@WordLearningTest
                        parentViewModel.handleUserAnswer(option.isCorrect)
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.uiState
                    .distinctUntilChanged { old, new ->
                        old.currentWord == new.currentWord &&
                            old.currentTestMode == new.currentTestMode &&
                            old.questionToken == new.questionToken
                    }
                    .collect { state ->
                        state.currentWord?.let { word ->
                            viewModel.loadData(word.id, state.currentTestMode)
                        }
                    }
            }
        }
    }

    @Composable
    fun WordLearningTest(
        uiState: WordLearningTestViewModel.TestUiState,
        onOptionSelected: (Int) -> Unit
    ) {
        if (uiState.isLoading) {
            LoadingContent()
            return
        }

        val selectedIndex = uiState.selectedIndex
        val hasAnswered = uiState.hasAnswered

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = uiState.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (uiState.promptHint.isNotBlank()) {
                Text(
                    text = uiState.promptHint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.options.forEachIndexed { index, option ->
                    OptionItem(
                        modifier = Modifier.defaultMinSize(0.dp, 50.dp),
                        option = option,
                        isSelected = (selectedIndex == index) || (hasAnswered && option.isCorrect),
                        onClick = { onOptionSelected(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "\u9898\u76EE\u52A0\u8F7D\u4E2D...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            Text(
                text = "\u8BF7\u7A0D\u5019",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Color(0xFF666666)
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun WordLearningTestPreview() {
        MaterialTheme {
            val options = listOf(
                OptionData("v.", "示例释义 1", isCorrect = false),
                OptionData("n.", "示例释义 2", isCorrect = true),
                OptionData("n.", "示例释义 3", isCorrect = false),
                OptionData("adj.", "示例释义 4", isCorrect = false)
            )
            WordLearningTest(
                uiState = WordLearningTestViewModel.TestUiState(
                    mode = LearningTestMode.MEANING_CHOICE,
                    prompt = "从下列四个选项中，选择正确释义",
                    options = options
                ),
                onOptionSelected = {}
            )
        }
    }

    @Composable
    fun OptionItem(
        modifier: Modifier = Modifier,
        option: OptionData,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val shakeOffset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        val backgroundColor = when {
            isSelected && option.isCorrect -> Color(0xFF4CAF50)
            isSelected && !option.isCorrect -> Color(0xFFE57373)
            else -> Color(0xFFF5F5F5)
        }

        val textColor = if (isSelected) Color.White else Color.Black
        val borderColor = when {
            isSelected && option.isCorrect -> Color(0xFF388E3C)
            isSelected && !option.isCorrect -> Color(0xFFD32F2F)
            else -> Color.Transparent
        }

        Surface(
            onClick = {
                onClick()
                if (!option.isCorrect) {
                    scope.launch {
                        shakeOffset.snapTo(0f)
                        shakeOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                0f at 0
                                -16f at 50
                                16f at 100
                                -12f at 150
                                12f at 200
                                -8f at 250
                                8f at 300
                                0f at 400
                            }
                        )
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            tonalElevation = if (isSelected) 4.dp else 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = option.partOfSpeech,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor,
                        modifier = Modifier.width(40.dp)
                    )

                    Text(
                        text = option.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Icon(
                        painter = painterResource(id = if (option.isCorrect) R.drawable.module_learning_check else R.drawable.module_learning_clear),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

data class OptionData(
    val partOfSpeech: String,
    val content: String,
    val isCorrect: Boolean = false
)
