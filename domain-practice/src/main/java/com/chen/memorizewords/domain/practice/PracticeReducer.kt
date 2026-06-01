package com.chen.memorizewords.domain.practice
class PracticeReducer(
    private val questionFactory: PracticeQuestionFactory = DefaultPracticeQuestionFactory(),
    private val reviewPolicy: PracticeReviewPolicy = PracticeReviewPolicy(),
    private val reportPolicy: PracticeReportPolicy = PracticeReportPolicy()
) {
    fun reduce(
        state: PracticeSession,
        action: PracticeAction
    ): PracticeReduceResult {
        return when (action) {
            is PracticeAction.Start -> start(action)
            is PracticeAction.SubmitChoice -> submitChoice(state, action)
            is PracticeAction.SubmitText -> submitText(state, action)
            is PracticeAction.SubmitShadowing -> submitShadowing(state, action)
            PracticeAction.RevealAnswer -> revealAnswer(state)
            PracticeAction.Skip -> skip(state)
            PracticeAction.Continue -> continueToNext(state)
            PracticeAction.Reset -> PracticeReduceResult(emptySession())
        }
    }

    private fun start(action: PracticeAction.Start): PracticeReduceResult {
        val questions = questionFactory.buildQuestions(action.kind, action.words)
        val session = PracticeSession(
            sessionId = action.sessionId,
            kind = action.kind,
            totalQuestionCount = questions.size
        )
        if (questions.isEmpty()) {
            return finish(session)
        }
        val active = questions.first()
        val next = session.copy(
            phase = PracticePhase.ANSWERING,
            activeQuestion = active,
            pendingQuestions = questions.drop(1)
        )
        return PracticeReduceResult(
            state = next,
            effects = active.speechRequestEffect()
        )
    }

    private fun submitChoice(
        state: PracticeSession,
        action: PracticeAction.SubmitChoice
    ): PracticeReduceResult {
        val question = state.activeQuestion as? MeaningChoiceQuestion ?: return PracticeReduceResult(state)
        if (!state.canAnswer(action.questionId)) return PracticeReduceResult(state)
        val isCorrect = question.choices.any { it.id == action.choiceId && it.isCorrect }
        return applyAnswer(
            state = state,
            question = question,
            status = if (isCorrect) PracticeAnswerStatus.CORRECT else PracticeAnswerStatus.WRONG,
            submittedAnswer = action.choiceId,
            score = null
        )
    }

    private fun submitText(
        state: PracticeSession,
        action: PracticeAction.SubmitText
    ): PracticeReduceResult {
        val question = state.activeQuestion ?: return PracticeReduceResult(state)
        if (!state.canAnswer(action.questionId)) return PracticeReduceResult(state)
        val isCorrect = when (question) {
            is SpellingQuestion -> normalize(action.text) == normalize(question.answer)
            is AudioLoopQuestion -> action.text.isNotBlank()
            is MeaningChoiceQuestion,
            is ShadowingQuestion -> false
        }
        return applyAnswer(
            state = state,
            question = question,
            status = if (isCorrect) PracticeAnswerStatus.CORRECT else PracticeAnswerStatus.WRONG,
            submittedAnswer = action.text,
            score = null
        )
    }

    private fun submitShadowing(
        state: PracticeSession,
        action: PracticeAction.SubmitShadowing
    ): PracticeReduceResult {
        val question = state.activeQuestion as? ShadowingQuestion ?: return PracticeReduceResult(state)
        if (!state.canAnswer(action.questionId)) return PracticeReduceResult(state)
        return applyAnswer(
            state = state,
            question = question,
            status = if (action.passed) PracticeAnswerStatus.CORRECT else PracticeAnswerStatus.WRONG,
            submittedAnswer = action.score?.toString(),
            score = action.score
        )
    }

    private fun revealAnswer(state: PracticeSession): PracticeReduceResult {
        val question = state.activeQuestion ?: return PracticeReduceResult(state)
        if (state.phase != PracticePhase.ANSWERING) return PracticeReduceResult(state)
        return applyAnswer(
            state = state,
            question = question,
            status = PracticeAnswerStatus.REVEALED,
            submittedAnswer = null,
            score = null
        )
    }

    private fun skip(state: PracticeSession): PracticeReduceResult {
        val question = state.activeQuestion ?: return PracticeReduceResult(state)
        if (state.phase != PracticePhase.ANSWERING) return PracticeReduceResult(state)
        return applyAnswer(
            state = state,
            question = question,
            status = PracticeAnswerStatus.SKIPPED,
            submittedAnswer = null,
            score = null
        )
    }

    private fun applyAnswer(
        state: PracticeSession,
        question: PracticeQuestion,
        status: PracticeAnswerStatus,
        submittedAnswer: String?,
        score: Float?
    ): PracticeReduceResult {
        val record = PracticeAnswerRecord(
            questionId = question.id,
            wordId = question.word.id,
            status = status,
            submittedAnswer = submittedAnswer,
            expectedAnswer = question.answerLabel(),
            score = score
        )
        val history = state.history + record
        val shouldReview = reviewPolicy.shouldEnqueueAfterAnswer(
            question = question,
            historyBeforeAnswer = state.history,
            historyAfterAnswer = history,
            status = status
        )
        val nextQueue = if (shouldReview) {
            reviewPolicy.enqueueReview(state.reviewQuestions, question)
        } else {
            state.reviewQuestions
        }
        val nextPhase = if (shouldReview) PracticePhase.STUDYING else PracticePhase.FEEDBACK
        val effects = mutableListOf<PracticeEffect>(
            PracticeEffect.ShowFeedback(question.id, status)
        )
        if (shouldReview) {
            effects += PracticeEffect.ShowStudyCard(question.id, question.word.id)
        } else {
            effects += PracticeEffect.AdvanceAfterDelay(question.id)
        }
        return PracticeReduceResult(
            state = state.copy(
                phase = nextPhase,
                reviewQuestions = nextQueue,
                history = history
            ),
            effects = effects
        )
    }

    private fun continueToNext(state: PracticeSession): PracticeReduceResult {
        if (state.phase == PracticePhase.REPORT) return PracticeReduceResult(state)
        val nextQuestion = state.pendingQuestions.firstOrNull()
        if (nextQuestion != null) {
            val nextState = state.copy(
                phase = PracticePhase.ANSWERING,
                activeQuestion = nextQuestion,
                pendingQuestions = state.pendingQuestions.drop(1)
            )
            return PracticeReduceResult(nextState, nextQuestion.speechRequestEffect())
        }

        val reviewQuestion = state.reviewQuestions.firstOrNull()
        if (reviewQuestion != null && !reviewPolicy.shouldComplete(reviewQuestion, state.history)) {
            val nextState = state.copy(
                phase = PracticePhase.ANSWERING,
                activeQuestion = reviewQuestion,
                reviewQuestions = state.reviewQuestions.drop(1)
            )
            return PracticeReduceResult(nextState, reviewQuestion.speechRequestEffect())
        }

        return finish(state)
    }

    private fun finish(state: PracticeSession): PracticeReduceResult {
        val report = reportPolicy.buildReport(
            totalQuestionCount = state.totalQuestionCount,
            history = state.history
        )
        return PracticeReduceResult(
            state = state.copy(
                phase = PracticePhase.REPORT,
                activeQuestion = null,
                pendingQuestions = emptyList(),
                reviewQuestions = emptyList(),
                report = report
            ),
            effects = listOf(PracticeEffect.CompleteSession(report))
        )
    }

    private fun PracticeSession.canAnswer(questionId: String): Boolean {
        return phase == PracticePhase.ANSWERING && activeQuestion?.id == questionId
    }

    private fun PracticeQuestion.speechRequestEffect(): List<PracticeEffect> {
        val locale = speechLocale ?: return emptyList()
        return listOf(
            PracticeEffect.RequestSpeech(
                questionId = id,
                wordId = word.id,
                text = word.text,
                locale = locale
            )
        )
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase().replace(" ", "")
    }

    private fun emptySession(): PracticeSession {
        return PracticeSession(
            sessionId = "",
            kind = PracticeKind.LISTENING_MEANING
        )
    }
}
