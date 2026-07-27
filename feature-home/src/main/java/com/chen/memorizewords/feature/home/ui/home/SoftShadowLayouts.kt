package com.chen.memorizewords.feature.home.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.chen.memorizewords.feature.home.R

private class SoftShadowRenderer(context: Context, attrs: AttributeSet?) {
    private val ambientColor: Int
    private val ambientRadius: Float
    private val spotColor: Int
    private val spotRadius: Float
    private val spotDy: Float
    private val cornerRadius: Float
    private val fillColor: Int
    private val horizontalInset: Float
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bounds = RectF()

    init {
        val values = context.obtainStyledAttributes(attrs, R.styleable.FeatureHomeSoftShadow)
        ambientColor = values.getColor(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowAmbientColor,
            Color.TRANSPARENT,
        )
        ambientRadius = values.getDimension(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowAmbientRadius,
            0f,
        )
        spotColor = values.getColor(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowSpotColor,
            Color.TRANSPARENT,
        )
        spotRadius = values.getDimension(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowSpotRadius,
            0f,
        )
        spotDy = values.getDimension(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowSpotDy,
            0f,
        )
        cornerRadius = values.getDimension(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowCornerRadius,
            0f,
        )
        fillColor = values.getColor(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowFillColor,
            Color.WHITE,
        )
        horizontalInset = values.getDimension(
            R.styleable.FeatureHomeSoftShadow_feature_home_shadowHorizontalInset,
            0f,
        )
        values.recycle()
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        bounds.set(horizontalInset, 0f, width.toFloat() - horizontalInset, height.toFloat())
        paint.color = fillColor

        if (Color.alpha(ambientColor) > 0 && ambientRadius > 0f) {
            paint.setShadowLayer(ambientRadius, 0f, 0f, ambientColor)
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
        }
        if (Color.alpha(spotColor) > 0 && spotRadius > 0f) {
            paint.setShadowLayer(spotRadius, 0f, spotDy, spotColor)
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
        }
        paint.clearShadowLayer()
    }
}

class SoftShadowConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val shadow = SoftShadowRenderer(context, attrs)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun draw(canvas: Canvas) {
        shadow.draw(canvas, width, height)
        super.draw(canvas)
    }
}

class SoftShadowLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val shadow = SoftShadowRenderer(context, attrs)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun draw(canvas: Canvas) {
        shadow.draw(canvas, width, height)
        super.draw(canvas)
    }
}
