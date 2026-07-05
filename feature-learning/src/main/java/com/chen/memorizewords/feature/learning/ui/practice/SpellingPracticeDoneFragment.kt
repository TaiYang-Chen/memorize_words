package com.chen.memorizewords.feature.learning.ui.practice

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.chen.memorizewords.core.ui.ext.dpToPx
import com.chen.memorizewords.domain.word.query.WordDetail
import com.chen.memorizewords.domain.word.query.WordReadFacade
import com.chen.memorizewords.feature.learning.PracticeActivity
import com.chen.memorizewords.feature.learning.R
import com.chen.memorizewords.feature.learning.databinding.FragmentPracticeSpellingDoneBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SpellingPracticeDoneFragment : Fragment() {

    @Inject
    lateinit var wordReadFacade: WordReadFacade

    private var _binding: FragmentPracticeSpellingDoneBinding? = null
    private val binding: FragmentPracticeSpellingDoneBinding
        get() = requireNotNull(_binding)

    private var rows: List<SpellingQuestionResult> = emptyList()
    private var detailLoadJob: Job? = null
    private var isDetailVisible: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPracticeSpellingDoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rows = readRows()
        val summary = PracticeSessionSummary(
            questionCount = arguments?.getInt(ARG_QUESTION_COUNT).orZero(),
            completedCount = arguments?.getInt(ARG_COMPLETED_COUNT).orZero(),
            correctCount = arguments?.getInt(ARG_CORRECT_COUNT).orZero(),
            submitCount = arguments?.getInt(ARG_SUBMIT_COUNT).orZero(),
            hintCount = arguments?.getInt(ARG_HINT_COUNT).orZero()
        )
        renderSummary(summary)
        renderWrongRows(rows.filterNot { it.isCorrect })
        binding.btnClose.setOnClickListener { finishPractice() }
        binding.btnExit.setOnClickListener { finishPractice() }
        binding.btnDetailBack.setOnClickListener { hideDetail() }
        binding.btnPrimary.setOnClickListener { handlePrimaryAction() }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isDetailVisible) {
                        hideDetail()
                    } else {
                        finishPractice()
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        detailLoadJob?.cancel()
        detailLoadJob = null
        _binding = null
        super.onDestroyView()
    }

    private fun renderSummary(summary: PracticeSessionSummary) {
        val wrongCount = (summary.completedCount - summary.correctCount).coerceAtLeast(0)
        val accuracy = if (summary.completedCount > 0) {
            summary.correctCount * 100 / summary.completedCount
        } else {
            0
        }
        binding.tvAccuracy.text = getString(R.string.practice_spelling_done_accuracy_format, accuracy)
        binding.tvDoneSubtitle.text = getString(
            R.string.practice_spelling_done_subtitle,
            summary.completedCount,
            wrongCount
        )
        binding.statCompleted.tvStatValue.text = summary.completedCount.toString()
        binding.statCompleted.tvStatLabel.text = getString(R.string.practice_spelling_done_metric_completed)
        binding.statCorrect.tvStatValue.text = summary.correctCount.toString()
        binding.statCorrect.tvStatLabel.text = getString(R.string.practice_spelling_done_metric_correct)
        binding.statWrong.tvStatValue.text = wrongCount.toString()
        binding.statWrong.tvStatLabel.text = getString(R.string.practice_spelling_done_metric_wrong)
        binding.statHint.tvStatValue.text = summary.hintCount.toString()
        binding.statHint.tvStatLabel.text = getString(R.string.practice_spelling_done_metric_hint)
        binding.btnPrimary.text = getString(
            if (wrongCount > 0) {
                R.string.practice_spelling_done_retry_wrong
            } else {
                R.string.practice_spelling_done_retry_set
            }
        )
    }

    private fun renderWrongRows(wrongRows: List<SpellingQuestionResult>) {
        binding.tvReviewEmpty.isVisible = wrongRows.isEmpty()
        binding.layoutWrongWords.isVisible = wrongRows.isNotEmpty()
        binding.layoutWrongWords.removeAllViews()
        wrongRows.forEach { row ->
            binding.layoutWrongWords.addView(createWrongRowView(row))
        }
    }

    private fun createWrongRowView(row: SpellingQuestionResult): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = requireContext().getDrawable(R.drawable.module_learning_bg_summary_panel)
            isClickable = true
            isFocusable = true
            setPadding(16.dpToPx(requireContext()), 14.dpToPx(requireContext()), 16.dpToPx(requireContext()), 14.dpToPx(requireContext()))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10.dpToPx(requireContext()))
            }
            addView(TextView(requireContext()).apply {
                text = row.word
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 20f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(requireContext()).apply {
                text = getString(
                    R.string.practice_spelling_done_wrong_answer_format,
                    row.submittedAnswer.ifBlank { "-" },
                    row.expectedAnswer
                )
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                setPadding(0, 8.dpToPx(requireContext()), 0, 0)
            })
            setOnClickListener { showDetail(row.wordId, row.word) }
        }
    }

    private fun handlePrimaryAction() {
        val wrongIds = rows.filterNot { it.isCorrect }.map { it.wordId }.distinct().toLongArray()
        val activity = activity as? PracticeActivity ?: return
        if (wrongIds.isNotEmpty()) {
            activity.restartSpellingPracticeWithWrongWords(wrongIds)
        } else {
            activity.restartCurrentSpellingPracticeSet()
        }
    }

    private fun showDetail(wordId: Long, fallbackWord: String) {
        if (wordId <= 0L) return
        isDetailVisible = true
        binding.layoutDetail.isVisible = true
        binding.detailScroll.scrollTo(0, 0)
        binding.tvDetailWord.text = fallbackWord
        binding.tvDetailPhonetic.text = ""
        binding.tvDetailLoading.text = getString(R.string.practice_spelling_detail_loading)
        binding.tvDetailLoading.isVisible = true
        binding.layoutDetailContent.isVisible = false
        detailLoadJob?.cancel()
        detailLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) {
                wordReadFacade.getWordDetailById(wordId)
            }
            if (!isAdded || !isDetailVisible) return@launch
            if (detail == null) {
                binding.tvDetailLoading.text = getString(R.string.word_detail_unavailable)
                return@launch
            }
            renderDetail(detail)
        }
    }

    private fun hideDetail() {
        isDetailVisible = false
        binding.layoutDetail.isVisible = false
        detailLoadJob?.cancel()
        detailLoadJob = null
    }

    private fun renderDetail(detail: WordDetail) {
        val word = detail.word
        binding.tvDetailLoading.isVisible = false
        binding.layoutDetailContent.isVisible = true
        binding.tvDetailWord.text = word.word
        binding.tvDetailPhonetic.text = word.phoneticUS.orEmpty().ifBlank {
            word.phoneticUK.orEmpty()
        }
        binding.layoutDetailDefinitions.removeAllViews()
        val definitions = detail.definitions
        if (definitions.isEmpty()) {
            binding.layoutDetailDefinitions.addView(
                createDetailTextView(getString(R.string.learning_share_empty_definitions))
            )
        } else {
            definitions.forEach { definition ->
                binding.layoutDetailDefinitions.addView(
                    createDetailTextView(
                        "${definition.partOfSpeech.name.lowercase()}. ${definition.meaningChinese}"
                    )
                )
            }
        }
        val examples = detail.examples.take(3)
        binding.tvDetailExamplesTitle.isVisible = examples.isNotEmpty()
        binding.layoutDetailExamples.isVisible = examples.isNotEmpty()
        binding.layoutDetailExamples.removeAllViews()
        examples.forEach { example ->
            val text = buildString {
                append(example.englishSentence)
                example.chineseTranslation?.takeIf { it.isNotBlank() }?.let { translation ->
                    append('\n')
                    append(translation)
                }
            }
            binding.layoutDetailExamples.addView(createDetailTextView(text))
        }
    }

    private fun createDetailTextView(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#334155"))
            setLineSpacing(2.dpToPx(requireContext()).toFloat(), 1f)
            setPadding(0, 4.dpToPx(requireContext()), 0, 8.dpToPx(requireContext()))
        }
    }

    private fun finishPractice() {
        requireActivity().finish()
    }

    private fun readRows(): List<SpellingQuestionResult> {
        val wordIds = arguments?.getLongArray(ARG_WORD_IDS) ?: LongArray(0)
        val words = arguments?.getStringArrayList(ARG_WORDS) ?: emptyList()
        val submitted = arguments?.getStringArrayList(ARG_SUBMITTED) ?: emptyList()
        val expected = arguments?.getStringArrayList(ARG_EXPECTED) ?: emptyList()
        val correct = arguments?.getBooleanArray(ARG_CORRECT) ?: BooleanArray(0)
        val hintUsed = arguments?.getBooleanArray(ARG_HINT_USED) ?: BooleanArray(0)
        val revealed = arguments?.getBooleanArray(ARG_REVEALED) ?: BooleanArray(0)
        val attempts = arguments?.getIntArray(ARG_ATTEMPTS) ?: IntArray(0)
        return wordIds.indices.mapNotNull { index ->
            SpellingQuestionResult(
                wordId = wordIds.getOrNull(index) ?: return@mapNotNull null,
                word = words.getOrNull(index).orEmpty(),
                submittedAnswer = submitted.getOrNull(index).orEmpty(),
                expectedAnswer = expected.getOrNull(index).orEmpty(),
                isCorrect = correct.getOrNull(index) ?: false,
                hintUsed = hintUsed.getOrNull(index) ?: false,
                revealed = revealed.getOrNull(index) ?: false,
                attemptCount = attempts.getOrNull(index) ?: 0
            )
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    companion object {
        const val TAG = "spelling_practice_done"

        private const val ARG_QUESTION_COUNT = "question_count"
        private const val ARG_COMPLETED_COUNT = "completed_count"
        private const val ARG_CORRECT_COUNT = "correct_count"
        private const val ARG_SUBMIT_COUNT = "submit_count"
        private const val ARG_HINT_COUNT = "hint_count"
        private const val ARG_WORD_IDS = "word_ids"
        private const val ARG_WORDS = "words"
        private const val ARG_SUBMITTED = "submitted"
        private const val ARG_EXPECTED = "expected"
        private const val ARG_CORRECT = "correct"
        private const val ARG_HINT_USED = "hint_used"
        private const val ARG_REVEALED = "revealed"
        private const val ARG_ATTEMPTS = "attempts"

        fun newInstance(result: SpellingCompletionResult): SpellingPracticeDoneFragment {
            val rows = result.rows
            return SpellingPracticeDoneFragment().apply {
                arguments = bundleOf(
                    ARG_QUESTION_COUNT to result.summary.questionCount,
                    ARG_COMPLETED_COUNT to result.summary.completedCount,
                    ARG_CORRECT_COUNT to result.summary.correctCount,
                    ARG_SUBMIT_COUNT to result.summary.submitCount,
                    ARG_HINT_COUNT to result.summary.hintCount,
                    ARG_WORD_IDS to rows.map { it.wordId }.toLongArray(),
                    ARG_WORDS to ArrayList(rows.map { it.word }),
                    ARG_SUBMITTED to ArrayList(rows.map { it.submittedAnswer }),
                    ARG_EXPECTED to ArrayList(rows.map { it.expectedAnswer }),
                    ARG_CORRECT to rows.map { it.isCorrect }.toBooleanArray(),
                    ARG_HINT_USED to rows.map { it.hintUsed }.toBooleanArray(),
                    ARG_REVEALED to rows.map { it.revealed }.toBooleanArray(),
                    ARG_ATTEMPTS to rows.map { it.attemptCount }.toIntArray()
                )
            }
        }
    }
}
