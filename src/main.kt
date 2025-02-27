import korlibs.event.Key
import korlibs.time.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.image.paint.GradientPaint
import korlibs.image.paint.LinearGradientPaint
import korlibs.image.text.TextAlignment.Companion.MIDDLE_LEFT
import korlibs.io.file.std.*
import korlibs.korge.input.keys
import korlibs.korge.view.roundRect
import korlibs.korge.view.vector.gpuShapeView
import korlibs.math.geom.*
import korlibs.math.interpolation.*


val windowSize = Size(512, 512)
val threadHeight = windowSize.height/5

val yShift = threadHeight*0.15
val strokeThickness = yShift / 3
val cornerR = threadHeight / 20

val textSize = threadHeight / 2
val textXShift = textSize/4


suspend fun main() = Korge(virtualSize = windowSize, windowSize = windowSize, backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()


    sceneContainer.changeTo { MyScene() }
}


sealed interface ExecutionArea

data class SelfExecutionArea(val width: Number) : ExecutionArea

data class FrameExecution(val functionName: String, val areas: List<ExecutionArea>, var depth: Int = 0) : ExecutionArea

val longExecutionLen = windowSize.width/5
val shortExecutionLen = longExecutionLen/4


fun MutableList<ExecutionArea>.frameExecution(functionName: String, builder: MutableList<ExecutionArea>.() -> Unit) {
    add(FrameExecution(functionName, mutableListOf<ExecutionArea>().apply(builder)))
}

fun MutableList<ExecutionArea>.selfExecutionArea(width: Number) {
    add(SelfExecutionArea(width))
}

fun adjustDepth(execution: FrameExecution, depth: Int) {
    execution.depth = depth
    for (call in execution.areas) {
        if (call is FrameExecution) adjustDepth(call, depth + 1)
    }
}


val depthColorList = listOf(Colors.ORANGE, Colors.BLUE, Colors.DARKGREEN)

fun createUiFromExecution(execution: FrameExecution): Container {
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
                startingX += area.width.toDouble()
            }
            is FrameExecution -> {
                val ui = createUiFromExecution(area)
                ui.x = startingX
                ui.y = yShift/2
                container.addChild(ui)
                startingX += ui.width
            }
        }
    }

    val fillColor = depthColorList[execution.depth % depthColorList.size]
    container.addChildAt(text, 0)
    RoundRect(Size(startingX, rectHeight), RectCorners(cornerR), fill = fillColor, stroke = Colors.WHITE, strokeThickness = strokeThickness).let {
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

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val firstThreadY = windowSize.width/5

        solidRect(windowSize.width, threadHeight, Colors.BLUEVIOLET) {
            y = firstThreadY
        }

        val execution = FrameExecution("main", buildList {
            selfExecutionArea(longExecutionLen)
            frameExecution("foo") {
                selfExecutionArea(shortExecutionLen)
                frameExecution("boo") {
                    selfExecutionArea(longExecutionLen)
                }
                selfExecutionArea(shortExecutionLen)
            }
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
            selfExecutionArea(longExecutionLen)
        })

        adjustDepth(execution, 0)


        val chunk = createUiFromExecution(execution)
        addChild(chunk)

        horizontalGradientContainer(Size(windowSize.width/3, windowSize.height), 1.0, 0.0).also {
            addChild(it)
        }
        val rightShadowWidth = windowSize.width / 5
        horizontalGradientContainer(Size(rightShadowWidth, windowSize.height), 0.0, 1.0).also {
            addChild(it)
            it.x = windowSize.width - rightShadowWidth
        }

        val chunkWidth = chunk.width
        chunk.y = firstThreadY
        roundRect(Size(cornerR*2, windowSize.height), RectCorners(cornerR), fill = Colors.WHITE.withAd(0.5)) {
            x = (windowSize.width - width)/2
        }
        chunk.tween(chunk::x[windowSize.width, 0.0 - chunkWidth], time = 10.seconds, easing = Easing.LINEAR)
    }
}
