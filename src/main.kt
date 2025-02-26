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


val windowSize = Size(512, 512)*2
val threadHeight = windowSize.height/5

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
    val yShift = threadHeight*0.15
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
    RoundRect(Size(startingX, rectHeight), RectCorners(threadHeight/20), fill = fillColor, stroke = Colors.WHITE, strokeThickness = yShift/3).let {
        container.addChildAt(it, 0)
    }
    return container
}


fun horizontalGradientContainer(gradientSize: Size) = Container().apply {
    val mainStrokePaint = LinearGradientPaint(0, 0, gradientSize.width, 0).addColorStop(0.0, Colors.BLACK.withAd(1.0)).addColorStop(1.0, Colors.BLACK.withAd(0.0))
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

        horizontalGradientContainer(Size(windowSize.width/3, windowSize.height)).also {
            addChild(it)
        }

        val chunkWidth = chunk.width
        chunk.y = firstThreadY
        chunk.tween(chunk::x[windowSize.width, 0.0 - chunkWidth], time = 10.seconds, easing = Easing.LINEAR)


        val minDegrees = (-16).degrees
        val maxDegrees = (+16).degrees


        val image = image(resourcesVfs["korge.png"].readBitmap()) {
            rotation = maxDegrees
            anchor(.5, .5)
            scale(0.8)
            position(256, 256)
        }


        while (true) {
            image.tween(image::rotation[minDegrees], time = 1.seconds, easing = Easing.EASE_IN_OUT)
            image.tween(image::rotation[maxDegrees], time = 1.seconds, easing = Easing.EASE_IN_OUT)
        }
    }
}
