import korlibs.image.color.Colors
import korlibs.image.paint.LinearGradientPaint
import korlibs.image.text.TextAlignment.Companion.MIDDLE_LEFT
import korlibs.korge.Korge
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tween.get
import korlibs.korge.tween.tween
import korlibs.korge.view.*
import korlibs.korge.view.vector.gpuShapeView
import korlibs.math.geom.Anchor
import korlibs.math.geom.RectCorners
import korlibs.math.geom.Size
import korlibs.math.interpolation.Easing
import korlibs.time.seconds
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue


val windowSize = Size(512, 512)*2
val threadHeight = windowSize.height/5

val yShift = threadHeight*0.15
val globalStrokeThickness = yShift / 3
val cornerR = threadHeight / 20

val textSize = threadHeight / 2
val textXShift = textSize/4


suspend fun main() = Korge(virtualSize = windowSize, windowSize = windowSize, backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()


    sceneContainer.changeTo { MyScene() }
}


sealed interface ExecutionArea

data class SelfExecutionArea(val width: Number, val event: TimelineEventType?) : ExecutionArea

data class FrameExecution(val functionName: String, val areas: List<ExecutionArea>, var depth: Int = 0) : ExecutionArea

val longExecutionLen = windowSize.width/5
val shortExecutionLen = longExecutionLen/4


fun MutableList<ExecutionArea>.frameExecution(functionName: String, builder: MutableList<ExecutionArea>.() -> Unit) {
    add(FrameExecution(functionName, mutableListOf<ExecutionArea>().apply(builder)))
}

fun MutableList<ExecutionArea>.selfExecutionArea(width: Number, event: TimelineEventType? = null) {
    add(SelfExecutionArea(width, event))
}

fun adjustDepth(execution: FrameExecution, depth: Int) {
    execution.depth = depth
    for (call in execution.areas) {
        if (call is FrameExecution) adjustDepth(call, depth + 1)
    }
}

val depthColorList = listOf(Colors.ORANGE, Colors.BLUE, Colors.DARKGREEN)

class CollectedInfo {
    val breakPointPositions = mutableListOf<Double>()
}

fun CollectedInfo.createUiFromExecution(execution: FrameExecution, xTopShift: Double): Container {
    val container = Container()
    val rectHeight = threadHeight - yShift*execution.depth

    val text = Text(execution.functionName, textSize = textSize, alignment = MIDDLE_LEFT).also {
        it.y = rectHeight/2
        it.x = textXShift
    }

    var startingX = text.width + textXShift
    for (area in execution.areas) {
        when (area) {
            is SelfExecutionArea -> {
                if (area.event != null) {
                    val centerX = startingX + area.width.toDouble() / 2.0
                    if (area.event == TimelineEventType.Breakpoint) {
                        lineLikeRect(rectHeight).also {
                            it.x = centerX - it.width/2
                            it.fill = Colors.BROWN
                            container.addChild(it)
                        }
                    }
                    breakPointPositions.add(xTopShift + centerX)
                }
                startingX += area.width.toDouble()
            }
            is FrameExecution -> {
                val ui = createUiFromExecution(area, xTopShift + startingX)
                ui.x = startingX
                ui.y = yShift/2
                container.addChild(ui)
                startingX += ui.width
            }
        }
    }

    val fillColor = depthColorList[execution.depth % depthColorList.size]
    container.addChildAt(text, 0)
    RoundRect(Size(startingX, rectHeight), RectCorners(cornerR), fill = fillColor, stroke = Colors.WHITE, strokeThickness = globalStrokeThickness).let {
        container.addChildAt(it, 0)
    }
    return container
}


fun horizontalGradientContainer(gradientSize: Size, leftAlpha: Double, rightAlpha: Double) = Container().apply {
    val mainStrokePaint = LinearGradientPaint(0, 0, gradientSize.width, 0)
        .addColorStop(0.0, Colors.BLACK.withAd(leftAlpha))
        .addColorStop(1.0, Colors.BLACK.withAd(rightAlpha))
    gpuShapeView({
        this.fill(mainStrokePaint) {
            this.rect(0.0, 0.0, gradientSize.width, gradientSize.height)
        }
    }) {
    }
}


enum class TimelineEventType {
    Breakpoint,
    SteppingEnd,
    EndOfAnimation,
}

data class TimelineEvent(val timeX: Double, val type: TimelineEventType)

data class TreadUiData(val treadY: Double)

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val firstThread = TreadUiData(windowSize.width/5)

        solidRect(windowSize.width, threadHeight, Colors.BLUEVIOLET) {
            y = firstThread.treadY
        }

        val execution = FrameExecution("main", buildList {
            selfExecutionArea(longExecutionLen)
            frameExecution("foo") {
                selfExecutionArea(longExecutionLen)
                selfExecutionArea(shortExecutionLen, TimelineEventType.Breakpoint)
                frameExecution("boo") {
                    selfExecutionArea(longExecutionLen)
                }
                selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd)
                frameExecution("bar") {
                    selfExecutionArea(longExecutionLen, TimelineEventType.Breakpoint)
                }
            }
            selfExecutionArea(shortExecutionLen)
        })

        adjustDepth(execution, 0)

        val collectedInfo = CollectedInfo()
        val chunk = collectedInfo.createUiFromExecution(execution, 0.0)

        val timeLineEvents = collectedInfo.breakPointPositions.map { TimelineEvent(it, TimelineEventType.Breakpoint) } +
                TimelineEvent(chunk.width, TimelineEventType.EndOfAnimation)
        addChild(chunk)

        horizontalGradientContainer(Size(windowSize.width/3, windowSize.height), 1.0, 0.0).also {
            addChild(it)
        }
        val rightShadowWidth = windowSize.width / 5
        horizontalGradientContainer(Size(rightShadowWidth, windowSize.height), 0.0, 1.0).also {
            addChild(it)
            it.x = windowSize.width - rightShadowWidth
        }

        chunk.y = firstThread.treadY
        lineLikeRect(windowSize.height).let {
            it.fill = Colors.WHITE.withAd(0.5)
            it.x = (windowSize.width - it.width)/2
            addChild(it)
        }

        chunk.x = windowSize.width

        val pauseR = threadHeight / 5

        for (event in timeLineEvents) {
            val start = chunk.x
            val end = windowSize.width / 2 - event.timeX

            val relativeSize = (end - start).absoluteValue / windowSize.width

            chunk.tween(chunk::x[start, end], time = (relativeSize*3).seconds, easing = Easing.LINEAR)
            if (event.type != TimelineEventType.EndOfAnimation) {
                val c = Circle(pauseR).apply {
                    anchor = Anchor.CENTER
                    x = (windowSize.width + width) / 2 + pauseR
                    y = firstThread.treadY - pauseR
                    stroke = Colors.WHITE
                    strokeThickness = globalStrokeThickness
                    fill = Colors.BROWN
                }
                addChild(c)
                delay(1000)
                removeChild(c)
            }
        }
    }
}

private fun lineLikeRect(h: Double): RoundRect = RoundRect(Size(cornerR * 2, h), RectCorners(cornerR))
