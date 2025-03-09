import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.image.format.readBitmap
import korlibs.image.paint.LinearGradientPaint
import korlibs.image.text.TextAlignment.Companion.MIDDLE_LEFT
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.Korge
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tween.get
import korlibs.korge.tween.tween
import korlibs.korge.view.*
import korlibs.korge.view.vector.gpuShapeView
import korlibs.math.geom.Anchor
import korlibs.math.geom.Point
import korlibs.math.geom.RectCorners
import korlibs.math.geom.Size
import korlibs.math.interpolation.Easing
import korlibs.time.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.min

val windowSize = Size(1024, 512)
val threadHeight = windowSize.height/8

val yShift = threadHeight*0.15
val globalStrokeThickness = yShift / 3
val cornerR = threadHeight / 20
val lineLikeRectWidth = cornerR * 2

val functionTextSize = threadHeight / 2
val coroutineTextSize = threadHeight / 3
val textXShift = functionTextSize/4
val pauseR = threadHeight / 5

val longExecutionLen = windowSize.width/5
val shortExecutionLen = longExecutionLen/4

val speedupAllAnimationCoefficient = 1.0

val slowRunningAnimationCoefficient = 5000.0 / speedupAllAnimationCoefficient

val timeForExtendMs = (300L / speedupAllAnimationCoefficient).toLong()
val timeShowCoroutineFilterMs = (1500L / speedupAllAnimationCoefficient).toLong()
val waitOnBreakpoint = (1000L / speedupAllAnimationCoefficient).toLong()
val shortWaitOnPause = (300L / speedupAllAnimationCoefficient).toLong()

suspend fun main() = Korge(virtualSize = windowSize, windowSize = windowSize, backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()


    sceneContainer.changeTo { MyScene() }
}


sealed interface ExecutionArea


data class EventAndNextRunningType(
    val event: TimelineEventType,
    val nextRunningType: RunningType,
    val injection: FrameExecution? = null,
    val onInjectionEnd: EventAndNextRunningType? = null,
)

class SelfExecutionArea(val width: Number, val eventAndNextRunningType: EventAndNextRunningType?) : ExecutionArea


sealed interface FrameType {
    object NormalFunction : FrameType
    object Evaluation : FrameType

    data class CoroutineBorder(val coroutineName: String, val hasStart: Boolean, val hasEnd: Boolean) : FrameType
}

class FrameExecution(
    val functionName: String,
    val areas: List<ExecutionArea>,
    val frameType: FrameType = FrameType.NormalFunction,
    var depth: Int = 0,
    var parent: FrameExecution? = null,
    var topChunk: Container? = null,
) : ExecutionArea


fun MutableList<ExecutionArea>.frameExecution(functionName: String, frameType: FrameType = FrameType.NormalFunction, builder: MutableList<ExecutionArea>.() -> Unit) {
    add(FrameExecution(functionName, mutableListOf<ExecutionArea>().apply(builder), frameType))
}

fun MutableList<ExecutionArea>.selfExecutionArea(width: Number, eventAndNextRunningType: EventAndNextRunningType? = null) {
    add(SelfExecutionArea(width, eventAndNextRunningType))
}

fun MutableList<ExecutionArea>.selfExecutionArea(width: Number, event: TimelineEventType, nextRunningType: RunningType) {
    add(SelfExecutionArea(width, EventAndNextRunningType(event, nextRunningType)))
}

fun adjustDepth(execution: FrameExecution, depth: Int, parent: FrameExecution?) {
    execution.depth = depth
    execution.parent = parent
    for (call in execution.areas) {
        if (call is FrameExecution) adjustDepth(call, depth + 1, execution)
    }
}

val depthColorList = listOf(RGBA(100, 100, 150), RGBA(100, 100, 190), RGBA(100, 100, 230))

data class TimelineEvent(
    var absPosition: Double,
    val eventAndNextRunningType: EventAndNextRunningType,
    val frame: FrameExecution,
    val container: Container,
    var relativePosition: Double = absPosition
)

val FrameExecution.topFrame: FrameExecution get() = parent?.topFrame ?: this

class CollectedInfo {
    val breakPointPositions = mutableListOf<TimelineEvent>()
    var topChunk: Container? = null
}

fun CollectedInfo.createUiFromExecution(execution: FrameExecution, xFirstFrameShift: Double): Container {
    val container = Container()
    if (topChunk == null) topChunk = container
    execution.topChunk = topChunk
    val rectHeight = threadHeight - yShift*execution.depth

    val text = Text(execution.functionName, textSize = functionTextSize, alignment = MIDDLE_LEFT).also {
        it.y = rectHeight/2
        it.x = textXShift
    }

    var startingX = text.width + textXShift
    for (area in execution.areas) {
        when (area) {
            is SelfExecutionArea -> {
                if (area.eventAndNextRunningType != null) {
                    val centerX = startingX + area.width.toDouble() / 2.0
                    val event = area.eventAndNextRunningType.event
                    if (event.isBreakpoint) {
                        lineLikeRect(rectHeight).also {
                            it.x = centerX - it.width/2
                            it.fill = if (event.isTechnical) Colors.ORANGE else Colors.BROWN
                            container.addChild(it)
                        }
                    }
                    breakPointPositions.add(TimelineEvent(
                        xFirstFrameShift + centerX,
                        area.eventAndNextRunningType,
                        execution,
                        container,
                        centerX))
                }
                startingX += area.width.toDouble()
            }
            is FrameExecution -> {
                val ui = createUiFromExecution(area, xFirstFrameShift + startingX)
                ui.x = startingX
                ui.y = yShift/2
                container.addChild(ui)
                startingX += ui.width
            }
        }
    }

    val frameType = execution.frameType

    val fillColor = if (frameType == FrameType.Evaluation) {
        Colors.ORANGE
    } else {
        depthColorList[execution.depth % depthColorList.size]
    }

    container.addChildAt(text, 0)
    val frameSize = Size(startingX, rectHeight)
    val r = RoundRect(frameSize, RectCorners(cornerR), fill = fillColor, stroke = Colors.WHITE, strokeThickness = globalStrokeThickness).also {
        container.addChildAt(it, 0)
    }

    if (frameType is FrameType.CoroutineBorder) {
        addCoroutineOutline(container, frameType, r)
    }

    return container
}

fun addCoroutineOutline(container: Container, frameType: FrameType.CoroutineBorder, r: RoundRect) {
    val neededColor = Colors.YELLOW
    val lineThickness = threadHeight / 20.0
    val hBorderShiftFromCenter = threadHeight*0.8

    val solidGap = threadHeight / 10.0
    val dottedGap = solidGap/2.0


    val frameSize = r.size
    val centerY = frameSize.height * 3 / 10

    container.solidRect(frameSize.width, lineThickness) {
        color = neededColor
        pos = Point(0.0, -hBorderShiftFromCenter + centerY)
    }

    container.solidRect(frameSize.width, lineThickness) {
        color = neededColor
        pos = Point(0.0, hBorderShiftFromCenter + centerY)
    }

    container.text(frameType.coroutineName, textSize = coroutineTextSize, color = neededColor) {
        pos = Point(lineThickness, -hBorderShiftFromCenter + centerY + lineThickness)
    }

    for ((xP, isBorder) in listOf(0.0 to frameType.hasStart, frameSize.width to frameType.hasEnd)) {

        container.graphics {
            stroke(neededColor, lineWidth = lineThickness) {
                var y = -hBorderShiftFromCenter + centerY
                if (isBorder) {
                    moveTo(0.0, y); lineTo(0.0, hBorderShiftFromCenter + centerY)
                } else {
                    while (y < hBorderShiftFromCenter + centerY) {
                        moveTo(0.0, y); lineTo(0.0, min(y + solidGap, hBorderShiftFromCenter + centerY))
                        y += solidGap + dottedGap
                    }
                }
            }
        }.also {
            it.x = xP
        }
    }
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
        //antialiased = true
    }
}

sealed interface RunningType {
    data object ResumeAll : RunningType
    data object ResumeThread : RunningType

    sealed interface ChangingState : RunningType

    data object Running : ChangingState

    data object ExecutionEnd : RunningType, ChangingState

    data class SteppingOver(val functionName: String) : RunningType, ChangingState
}

sealed interface TimelineEventType {
    val isPaused: Boolean
    val isBreakpoint: Boolean
    val isTechnical: Boolean get() = false

    interface AllPaused : TimelineEventType
    interface PermanentPaused : TimelineEventType

    interface ShortPaused : TimelineEventType {
        override val isTechnical: Boolean get() = true
    }

    abstract class TimelineEventTypeImpl(override val isPaused: Boolean, override val isBreakpoint: Boolean) : TimelineEventType

    object PermanentBreakpoint : TimelineEventTypeImpl(isPaused = true, isBreakpoint = true), PermanentPaused
    object SuspendAllBreakpoint : TimelineEventTypeImpl(isPaused = true, isBreakpoint = true), AllPaused

    object SkippedBreakpoint : TimelineEventTypeImpl(isPaused = false, isBreakpoint = true)
    object SteppingEnd : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false)
    object EvaluationEnd : TimelineEventTypeImpl(isPaused = false, isBreakpoint = false)
    object EndOfAnimation : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false)

    class SetFilterEvent(val filterText: String) : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false), ShortPaused

    object SuspendThreadPause : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false), ShortPaused

    object SuspendAllSteppingEnd : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false), AllPaused
    object SuspendAllSteppingPermanentEnd : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false), AllPaused, PermanentPaused

    object TechnicalThreadBreakpoint : TimelineEventTypeImpl(isPaused = true, isBreakpoint = true) {
        override val isTechnical: Boolean get() = true
    }

    class ConditionCheck(val checkText: String) : TimelineEventTypeImpl(isPaused = true, isBreakpoint = false) {
        override val isTechnical: Boolean get() = true
    }
}

class TreadUiData(val treadY: Double, val execution: FrameExecution, val threadName: String? = null)


//lateinit var startDebugImage: Image
//lateinit var stepOverImage: Image
//lateinit var resumeImage: Image

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val startDebugImage = Image(resourcesVfs["debug_dark.png"].readBitmap())
        val stepOverImage = Image(resourcesVfs["stepOver_dark.png"].readBitmap())
        val resumeImage = Image(resourcesVfs["resume_dark.png"].readBitmap())

        val threadsContainer = container()

        val isSynchronous = true
        val treadUiDataList = coroutineCase()

        horizontalGradientContainer(Size(windowSize.width/3, windowSize.height), 1.0, 0.0).also {
            addChild(it)
        }
        val rightShadowWidth = windowSize.width / 5
        horizontalGradientContainer(Size(rightShadowWidth, windowSize.height), 0.0, 1.0).also {
            addChild(it)
            it.x = windowSize.width - rightShadowWidth
        }


        var filterText: Text? = null
        fun setFilterText(text: String): Text {
            if (filterText != null) removeChild(filterText)
            return text(text, textSize = functionTextSize, color = Colors.ORANGE) {
                y = 0.0
                x = 0.0
            }.also {
                filterText = it
            }
        }

        var stateText: Text? = null
        fun setStateText(text: String) {
            if (stateText != null) removeChild(stateText)
            stateText = text(text, textSize = functionTextSize) {
                y = 0.0
                x = windowSize.width / 2 + lineLikeRectWidth
            }
        }

        val allTimeLineEvents = mutableListOf<TimelineEvent>()

        data class ThreadInfo(val treadUiData: TreadUiData, val chunk: Container, val timelineEvents: MutableList<TimelineEvent>)

        val allThreadInfos = mutableListOf<ThreadInfo>()

        for (treadUiData in treadUiDataList) {
            val execution = treadUiData.execution

            threadsContainer.solidRect(windowSize.width, threadHeight, Colors.BLUEVIOLET) {
                y = treadUiData.treadY
            }

            treadUiData.threadName?.let {
                threadsContainer.text(it, textSize = functionTextSize) {
                    x = windowSize.width / 2 - textXShift*2 - width
                    y = treadUiData.treadY + threadHeight/2 - height/2
                }
            }

            adjustDepth(execution, 0, null)

            val collectedInfo = CollectedInfo()
            val chunk = collectedInfo.createUiFromExecution(execution, 0.0)

            val timelineEvents = collectedInfo.breakPointPositions.toMutableList()
            allTimeLineEvents.addAll(timelineEvents)

            threadsContainer.addChild(chunk)

            chunk.y = treadUiData.treadY
            lineLikeRect(windowSize.height).let {
                it.fill = Colors.WHITE.withAd(0.5)
                it.x = (windowSize.width - it.width)/2
                addChild(it)
            }

            chunk.x = windowSize.width

            allThreadInfos.add(ThreadInfo(treadUiData, chunk, if (isSynchronous) allTimeLineEvents else timelineEvents))
        }

        val endEvent = TimelineEvent(
            allThreadInfos.maxOf { it.chunk.width },
            EventAndNextRunningType(TimelineEventType.EndOfAnimation, RunningType.Running),
            // useless here, just something
            allThreadInfos.first().treadUiData.execution,
            allThreadInfos.first().chunk,
        )

        if (isSynchronous) {
            allTimeLineEvents.sortBy { it.absPosition }
            allTimeLineEvents.add(endEvent)
        }
        else {
            for ((_, _, timelineEvents) in allThreadInfos) {
                timelineEvents.add(endEvent)
            }
        }

        animateAction(startDebugImage)

        setStateText("Running")

        val stopSignMap = mutableMapOf<TreadUiData, Circle>()

        var threadNowRunning: TreadUiData? = null
        var currentEventIndex = 0
        while (currentEventIndex < allTimeLineEvents.size) {
            val timelineEvent = allTimeLineEvents[currentEventIndex]
            val threadHoldingCurrentEvent: ThreadInfo =
                timelineEvent.frame.topFrame.let { top -> allThreadInfos.single { it.treadUiData.execution === top } }

            val end = windowSize.width / 2 - timelineEvent.absPosition
            val path = (end - threadHoldingCurrentEvent.chunk.x).absoluteValue

            fun timeFromPath(p: Double) = ((p / windowSize.width) * slowRunningAnimationCoefficient).milliseconds
            fun pathFromTime(ms: Long) = ms.toDouble() / slowRunningAnimationCoefficient * windowSize.width

            val runningThreads: List<ThreadInfo> = threadNowRunning?.let { t -> listOf(allThreadInfos.single { it.treadUiData == t }) } ?: allThreadInfos

            val stayingThreads = allThreadInfos.toSet() - runningThreads.toSet()

            coroutineScope {
                for ((treadUiData,  chunk, timelineEvents) in runningThreads) {
                    stopSignMap[treadUiData]?.let {
                        threadsContainer.removeChild(it)
                    }

                    launch {
                        val start = chunk.x
                        chunk.tween(chunk::x[start, start - path], time = timeFromPath(path), easing = Easing.LINEAR)
                    }
                }
                // staying threads are just staying
            }

            val (event, nextRunningType, injection, onInjectionEnd) = timelineEvent.eventAndNextRunningType

            if (event == TimelineEventType.EndOfAnimation) break

            if (event is TimelineEventType.AllPaused) {
                setStateText("Paused")
            }

            val remainRunning = if (event !is TimelineEventType.AllPaused) {
                runningThreads - threadHoldingCurrentEvent
            } else emptyList()

            fun CoroutineScope.continueRunRemainRunning(timeInMs: Long) {
                if (remainRunning.isEmpty()) return
                val pathFromTime = pathFromTime(timeInMs)
                for (threadRemainRunning in remainRunning) {
                    val chunk = threadRemainRunning.chunk
                    launch {
                        chunk.tween(chunk::x[chunk.x, chunk.x - pathFromTime], time = timeInMs.milliseconds, easing = Easing.LINEAR)
                    }
                }
            }

            if (event.isPaused) {
                //setStateText("Paused")

                for ((treadUiData, chunk, timelineEvents) in (allThreadInfos - remainRunning)) {
                    stopSignMap[treadUiData]?.let {
                        threadsContainer.removeChild(it)
                    }
                    val c = Circle(pauseR).apply {
                        anchor = Anchor.CENTER
                        x = (windowSize.width + width) / 2 + pauseR
                        y = treadUiData.treadY - pauseR
                        stroke = Colors.WHITE
                        strokeThickness = globalStrokeThickness
                        fill = if (event is TimelineEventType.ShortPaused) Colors.YELLOW else Colors.BROWN
                    }
                    threadsContainer.addChild(c)
                    stopSignMap[treadUiData] = c
                }

                if (event.isTechnical) {
                    // nothing
                } else if (event is TimelineEventType.PermanentPaused) {
                    delay(100000)
                } else {
                    coroutineScope {
                        launch {
                            delay(waitOnBreakpoint)
                        }
                        continueRunRemainRunning(waitOnBreakpoint)
                    }
                }

                if (injection != null) {
                    adjustDepth(injection, timelineEvent.frame.depth + 1, timelineEvent.frame)

                    val collectedInfo = CollectedInfo()
                    collectedInfo.topChunk = timelineEvent.frame.topChunk
                    val injectionChunk = collectedInfo.createUiFromExecution(injection, timelineEvent.absPosition + lineLikeRectWidth)
                    val extendBy = injectionChunk.width
                    var relativePosition = timelineEvent.relativePosition
                    coroutineScope {
                        var c = timelineEvent.container
                        while (true) {
                            val firstChild = c.children.firstOrNull() ?: break
                            if (firstChild !is RoundRect) break
                            launch {
                                firstChild.tween(firstChild::width[firstChild.width, firstChild.width + extendBy], time = timeForExtendMs.milliseconds)
                            }

                            for (view in c.children) {
                                if (view.x > relativePosition) {
                                    launch {
                                        view.tween(view::x[view.x, view.x + extendBy], time = timeForExtendMs.milliseconds)
                                    }
                                }
                                if (view is SolidRect) {
                                    // coroutine border
                                    launch {
                                        view.tween(view::width[view.width, view.width + extendBy], time = timeForExtendMs.milliseconds)
                                    }
                                }
                            }
                            relativePosition = c.x + c.width - 1
                            c = c.parent ?: break
                        }
//                        if (event is TimelineEventType.ShortPaused) {
//                            for (info in allThreadInfos) {
//                                if (info.treadUiData == threadHoldingCurrentEvent.treadUiData) continue
//                                launch {
//                                    info.chunk.tween(info.chunk::x[info.chunk.x, info.chunk.x - pathFromTime(timeForExtendMs)], time = timeForExtendMs.milliseconds)
//                                }
//                            }
//                        }
                        continueRunRemainRunning(timeForExtendMs)
                    }
                    timelineEvent.container.addChild(injectionChunk)
                    injectionChunk.x = timelineEvent.relativePosition + lineLikeRectWidth
                    injectionChunk.y = yShift/2

                    for (e2 in allTimeLineEvents) {
                        if (e2.frame.topFrame == timelineEvent.frame.topFrame && e2.absPosition > timelineEvent.absPosition) {
                            if (e2.frame == timelineEvent.frame) {
                                e2.relativePosition += extendBy
                            }
                            e2.absPosition += extendBy
                        }
                    }

                    allTimeLineEvents.add(TimelineEvent(
                        timelineEvent.absPosition + lineLikeRectWidth + extendBy,
                        onInjectionEnd ?: EventAndNextRunningType(TimelineEventType.EvaluationEnd, RunningType.ExecutionEnd),
                        injection,
                        injectionChunk,
                    ))
                    //allTimeLineEvents.sortBy { it.absPosition }

                    if (event !is TimelineEventType.ShortPaused) {
                        coroutineScope {
                            launch {
                                delay(waitOnBreakpoint)
                            }
                            continueRunRemainRunning(waitOnBreakpoint)
                        }
                    }
                }

                if (event is TimelineEventType.SetFilterEvent) {
                    coroutineScope {
                        launch {
                            setFilterText("Stepping Filter: " + event.filterText).let {
                                it.tween(it::alpha[0.0, 1.0], time = timeShowCoroutineFilterMs.milliseconds)
                            }
                        }
                        continueRunRemainRunning(timeShowCoroutineFilterMs)
                    }
                } else if (event is TimelineEventType.ShortPaused){
                    coroutineScope {
                        launch {
                            delay(shortWaitOnPause)
                        }
                        continueRunRemainRunning(shortWaitOnPause)
                    }
                }

                if (event is TimelineEventType.ConditionCheck) {
                    coroutineScope {
                        launch {
                            val c = container()
                            val t = Text(event.checkText, textSize = functionTextSize, color = Colors.ORANGE)
                            t.position(windowSize.width / 2 - t.width / 2, threadHoldingCurrentEvent.treadUiData.treadY + threadHeight)
                            val r = c.roundRect(t.size*1.2, RectCorners(cornerR), fill = Colors.BLACK, stroke = Colors.WHITE, globalStrokeThickness)
                            r.position(t.x - t.size.width*0.1, t.y - t.size.height*0.1)
                            c.addChild(t)

                            addChild(c)
                            c.tween(c::alpha[0.0, 1.0], time = timeForExtendMs.milliseconds)
                            delay(timeShowCoroutineFilterMs)
                            c.tween(c::alpha[1.0, 0.0], time = timeForExtendMs.milliseconds)
                        }
                        continueRunRemainRunning(2*timeForExtendMs + timeShowCoroutineFilterMs)
                    }
                }

                if (nextRunningType is RunningType.ChangingState) {
                    if (nextRunningType == RunningType.Running) {
                        animateAction(resumeImage)
                    }
                    if (nextRunningType is RunningType.SteppingOver) {
                        animateAction(stepOverImage)
                    }
                    setStateText(
                        when (nextRunningType) {
                            RunningType.Running -> "Running"
                            is RunningType.SteppingOver -> "Stepping Over ${nextRunningType.functionName}"
                            RunningType.ExecutionEnd -> "Done"
                        }
                    )
                }



                threadNowRunning = when (nextRunningType) {
                    RunningType.ResumeThread -> threadHoldingCurrentEvent.treadUiData
                    else -> null
                }
            }
            allTimeLineEvents.sortBy {
                it.absPosition + it.frame.topChunk!!.x
            }

            currentEventIndex++
        }

        setStateText("Done")
    }
}


private suspend fun Container.animateAction(image: Image) {
    val c = container()
    image.toHeight(windowSize.height/4)

    c.roundRect(image.scaledSize, RectCorners(image.scaledSize.height/20), fill = RGBA(50,  50, 50), stroke = Colors.WHITE, strokeThickness = globalStrokeThickness)
    c.addChild(image)

    addChild(c)
    c.y = windowSize.height/10
    c.x = windowSize.width/2 - image.scaledWidth/2

    c.tween(c::alpha[0.0, 1.0], time = timeShowCoroutineFilterMs.milliseconds)
    c.tween(c::alpha[1.0, 0.0], time = timeShowCoroutineFilterMs.milliseconds)
    removeChild(c)
}


private fun lineLikeRect(h: Double): RoundRect = RoundRect(Size(lineLikeRectWidth * 2, h), RectCorners(cornerR))


fun <T : View> T.toHeight(shouldBeHeight: Double): T {
    scaledHeight = shouldBeHeight
    scaleX = scaleY
    return this
}

fun <T : View> T.toWidth(shouldBeWidth: Double): T {
    scaledWidth = shouldBeWidth
    scaleY = scaleX
    return this
}
