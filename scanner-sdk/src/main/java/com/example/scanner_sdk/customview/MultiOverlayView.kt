package com.example.scanner_sdk.customview

/*
class MultiOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GREEN

    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        textSize = 36f
        typeface = Typeface.MONOSPACE

    }
    private var barcodes: List<Barcode> = emptyList()
    private var rotationDegrees: Int = 0

    private var imageToView: Matrix? = null
    private var imgW = 1
    private var imgH = 1
    private var isFlipped: Boolean = false
    private var rotationDeg = 0

    private val transformMatrix = Matrix()


    fun setResults(
        barcodes: List<Barcode>,
        meta: FrameMetadata
    ) {
        this.barcodes = barcodes
        this.imgW = meta.width
        this.imgH = meta.height
        this.isFlipped = meta.isFlipped
        this.rotationDeg = meta.rotation

        rebuildMatrix()
        invalidate()
    }

    private fun rebuildMatrix() {
        val viewW = width.toFloat().coerceAtLeast(1f)
        val viewH = height.toFloat().coerceAtLeast(1f)

        val scale = maxOf(viewW / imgW, viewH / imgH)

        val dx = (viewW - imgW * scale) / 2f
        val dy = (viewH - imgH * scale) / 2f

        transformMatrix.reset()

        val sx = if (isFlipped) -scale else scale
        val tx = if (isFlipped) (viewW - dx) else dx

        transformMatrix.postScale(sx, scale)
        transformMatrix.postTranslate(tx, dy)

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildMatrix()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (bc in barcodes) {
            val box = bc.boundingBox ?: continue
            val rectF = RectF(box)

            // map ML Kit rect into view space
            transformMatrix.mapRect(rectF)

            // draw the box
            canvas.drawRect(rectF, boxPaint)

            // draw the code text
            val value = bc.rawValue ?: "No Value"
            val tempValue = value.ifEmpty { "No Value" }
            Log.d("BarcodeAnalyzer", tempValue)
            if (value.isNotEmpty()) {
                canvas.drawText(
                    tempValue.take(30),
                    rectF.left + 8f,
                    rectF.top - 8f,
                    textPaint
                )
            }
        }
    }

}*/

