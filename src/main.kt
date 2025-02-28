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
import korlibs.time.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

val windowSize = Size(1024, 512)
val threadHeight = windowSize.height/8

val yShift = threadHeight*0.15
val globalStrokeThickness = yShift / 3
val cornerR = threadHeight / 20
val lineLikeRectWidth = cornerR * 2

val globalTextSize = threadHeight / 2
val textXShift = globalTextSize/4
val pauseR = threadHeight / 5


suspend fun main() = Korge(virtualSize = windowSize, windowSize = windowSize, backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()


    sceneContainer.changeTo { MyScene() }
}


sealed interface ExecutionArea


data class EventAndNextRunningType(val event: TimelineEventType, val nextRunningType: RunningType, val injection: FrameExecution? = null)

data class SelfExecutionArea(val width: Number, val eventAndNextRunningType: EventAndNextRunningType?) : ExecutionArea

data class FrameExecution(val functionName: String, val areas: List<ExecutionArea>, var depth: Int = 0, var parent: FrameExecution? = null) : ExecutionArea

val longExecutionLen = windowSize.width/5
val shortExecutionLen = longExecutionLen/4


fun MutableList<ExecutionArea>.frameExecution(functionName: String, builder: MutableList<ExecutionArea>.() -> Unit) {
    add(FrameExecution(functionName, mutableListOf<ExecutionArea>().apply(builder)))
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

val depthColorList = listOf(Colors.ORANGE, Colors.BLUE, Colors.DARKGREEN)

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
}

fun CollectedInfo.createUiFromExecution(execution: FrameExecution, xFirstFrameShift: Double): Container {
    val container = Container()
    val rectHeight = threadHeight - yShift*execution.depth

    val text = Text(execution.functionName, textSize = globalTextSize, alignment = MIDDLE_LEFT).also {
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
                            it.fill = Colors.BROWN
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
        //antialiased = true
    }
}

enum class RunningType {
    Running,
    SteppingOver,
    EvaluationAll,
    EvaluationThread,
}


enum class TimelineEventType(val isPaused: Boolean, val isBreakpoint: Boolean) {
    PermanentBreakpoint(isPaused = true, isBreakpoint = true),
    Breakpoint(isPaused = true, isBreakpoint = true),
    BreakpointTmpThread(isPaused = true, isBreakpoint = false),
    TmpBreakpoint(isPaused = true, isBreakpoint = false),
    SkippedBreakpoint(isPaused = false, isBreakpoint = true),
    SteppingEnd(isPaused = true, isBreakpoint = false),
    EvaluationEnd(isPaused = false, isBreakpoint = false),
    EndOfAnimation(isPaused = true, isBreakpoint = false)
}

class TreadUiData(val treadY: Double, val execution: FrameExecution)

fun simpleOnThreadCase(): List<TreadUiData> {
    val execution = FrameExecution("main", buildList {
        selfExecutionArea(longExecutionLen)
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen)
            selfExecutionArea(shortExecutionLen, TimelineEventType.Breakpoint, RunningType.SteppingOver)
            frameExecution("boo") {
                selfExecutionArea(longExecutionLen)
            }
            selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.SteppingOver)
            frameExecution("bar") {
                selfExecutionArea(longExecutionLen, TimelineEventType.Breakpoint, RunningType.Running)
            }
        }
        selfExecutionArea(shortExecutionLen)
    })

    return listOf(TreadUiData(windowSize.height/4, execution))
}

fun twoThreadsCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.Breakpoint, RunningType.SteppingOver)
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen)
        }
        selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("run", buildList {
        selfExecutionArea(longExecutionLen)
        for (i in 0..100) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })
    return listOf(
        TreadUiData(windowSize.height/4, execution1),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2),
    )
}

fun breakpointInAnotherThreadCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.Breakpoint, RunningType.SteppingOver)
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen*3)
        }
        selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("run", buildList {
        selfExecutionArea(longExecutionLen)

        frameExecution("another") {
            selfExecutionArea(longExecutionLen)
            selfExecutionArea(longExecutionLen, TimelineEventType.SkippedBreakpoint, RunningType.SteppingOver)
        }

        for (i in 0..100) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })
    return listOf(
        TreadUiData(windowSize.height/4, execution1),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2),
    )
}

fun suspendThreadModeCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.PermanentBreakpoint, RunningType.SteppingOver)
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen*3)
        }
        selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("run", buildList {
        selfExecutionArea(longExecutionLen)

        frameExecution("another") {
            selfExecutionArea(longExecutionLen)
            selfExecutionArea(longExecutionLen, TimelineEventType.PermanentBreakpoint, RunningType.Running)
        }

        for (i in 0..100) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution3 = FrameExecution("run2", buildList {
        selfExecutionArea(longExecutionLen)

        for (i in 0..100) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    return listOf(
        TreadUiData(windowSize.height/4, execution1),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2),
        TreadUiData(windowSize.height/4 + threadHeight*4, execution3),
    )
}


fun coroutineCase(): List<TreadUiData> {
    val getCoroutineInjection = FrameExecution("getCoroutineId", buildList {
        selfExecutionArea(shortExecutionLen)
    })


    val execution1 = FrameExecution("dispatch", buildList {
        selfExecutionArea(longExecutionLen)
        frameExecution("launch 1") {
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.Breakpoint, RunningType.EvaluationAll, getCoroutineInjection))
                selfExecutionArea(shortExecutionLen, TimelineEventType.TmpBreakpoint, RunningType.SteppingOver)
                frameExecution("func") {
                    selfExecutionArea(shortExecutionLen)
                }
            }
            //selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        }
        frameExecution("launch 2") {
            frameExecution("fff") {
                selfExecutionArea(shortExecutionLen)
            }
        }
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("dispatch", buildList {
        selfExecutionArea(longExecutionLen)
        for (i in 3..4) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("launch $i") {
                selfExecutionArea(longExecutionLen)
            }
        }
        selfExecutionArea(shortExecutionLen)
        frameExecution("launch 5") {
            selfExecutionArea(longExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.BreakpointTmpThread, RunningType.EvaluationThread, getCoroutineInjection))
//                selfExecutionArea(shortExecutionLen, TimelineEventType.TmpBreakpoint, RunningType.SteppingOver)
//                frameExecution("func") {
//                    selfExecutionArea(shortExecutionLen)
//                }
            }

        }
    })
    return listOf(
        TreadUiData(windowSize.height/4, execution1),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2),
    )
}

class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
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

        var stateText: Text? = null
        fun setStateText(text: String) {
            if (stateText != null) removeChild(stateText)
            stateText = text(text, textSize = globalTextSize) {
                y = 0.0
                x = windowSize.width / 2 + lineLikeRectWidth
            }
        }

        val allTimeLineEvents = mutableListOf<TimelineEvent>()

        data class ThreadInfo(val treadUiData: TreadUiData, val chunk: Container, val timelineEvents: MutableList<TimelineEvent>)

        val threadInfos = mutableListOf<ThreadInfo>()

        for (treadUiData in treadUiDataList) {
            val execution = treadUiData.execution

            threadsContainer.solidRect(windowSize.width, threadHeight, Colors.BLUEVIOLET) {
                y = treadUiData.treadY
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

            threadInfos.add(ThreadInfo(treadUiData, chunk, if (isSynchronous) allTimeLineEvents else timelineEvents))
        }

        val endEvent = TimelineEvent(
            threadInfos.maxOf { it.chunk.width },
            EventAndNextRunningType(TimelineEventType.EndOfAnimation, RunningType.Running),
            // useless here, just something
            threadInfos.first().treadUiData.execution,
            threadInfos.first().chunk,
        )

        if (isSynchronous) {
            allTimeLineEvents.sortBy { it.absPosition }
            allTimeLineEvents.add(endEvent)
        }
        else {
            for ((_, _, timelineEvents) in threadInfos) {
                timelineEvents.add(endEvent)
            }
        }

        setStateText("Running")

        val stopSignMap = mutableMapOf<TreadUiData, Circle>()

        var threadNowRunning: TreadUiData? = null
        var currentEventIndex = 0
        while (currentEventIndex < allTimeLineEvents.size) {
            val timelineEvent = allTimeLineEvents[currentEventIndex]
            val threadHoldingCurrentEvent: ThreadInfo =
                timelineEvent.frame.topFrame.let { top -> threadInfos.single { it.treadUiData.execution === top } }

            val end = windowSize.width / 2 - timelineEvent.absPosition
            val path = (end - threadHoldingCurrentEvent.chunk.x).absoluteValue

            fun timeFromPath(p: Double) = ((p / windowSize.width) * 3000).milliseconds
            fun pathFromTime(ms: Long) = ms.toDouble() / 3000.0 * windowSize.width

            val runningThreads: List<ThreadInfo> = threadNowRunning?.let { t -> listOf(threadInfos.single { it.treadUiData == t }) } ?: threadInfos

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
            }

            val event = timelineEvent.eventAndNextRunningType.event
            if (event == TimelineEventType.EndOfAnimation) break

            if (event.isPaused) {
                setStateText("Paused")

                for ((treadUiData, chunk, timelineEvents) in threadInfos) {
                    stopSignMap[treadUiData]?.let {
                        threadsContainer.removeChild(it)
                    }
                    val c = Circle(pauseR).apply {
                        anchor = Anchor.CENTER
                        x = (windowSize.width + width) / 2 + pauseR
                        y = treadUiData.treadY - pauseR
                        stroke = Colors.WHITE
                        strokeThickness = globalStrokeThickness
                        fill = Colors.BROWN
                    }
                    threadsContainer.addChild(c)
                    stopSignMap[treadUiData] = c
                }

                if (event == TimelineEventType.BreakpointTmpThread) {
                    // nothing
                } else if (event == TimelineEventType.PermanentBreakpoint) {
                    delay(100000)
                } else {
                    delay(1000)
                }

                val injection = timelineEvent.eventAndNextRunningType.injection

                if (injection != null) {
                    adjustDepth(injection, timelineEvent.frame.depth + 1, timelineEvent.frame)

                    val collectedInfo = CollectedInfo()
                    val injectionChunk = collectedInfo.createUiFromExecution(injection, timelineEvent.absPosition + lineLikeRectWidth)
                    val extendBy = injectionChunk.width
                    var relativePosition = timelineEvent.relativePosition
                    coroutineScope {
                        var c = timelineEvent.container
                        val timeForExtendMs = 300L
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
                            }
                            relativePosition = c.x + c.width - 1
                            c = c.parent ?: break
                        }
                        if (event == TimelineEventType.BreakpointTmpThread) {
                            for (info in threadInfos) {
                                if (info.treadUiData == threadHoldingCurrentEvent.treadUiData) continue
                                launch {
                                    info.chunk.tween(info.chunk::x[info.chunk.x, info.chunk.x - pathFromTime(timeForExtendMs)], time = timeForExtendMs.milliseconds)
                                }
                            }
                        }
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
                        EventAndNextRunningType(TimelineEventType.EvaluationEnd, RunningType.SteppingOver),
                        injection,
                        injectionChunk,
                    ))
                    allTimeLineEvents.sortBy { it.absPosition }

                    if (event != TimelineEventType.BreakpointTmpThread) {
                        delay(1000)
                    }
                }

                setStateText(
                    when (timelineEvent.eventAndNextRunningType.nextRunningType) {
                        RunningType.EvaluationAll, RunningType.EvaluationThread -> "Evaluation"
                        RunningType.Running -> "Running"
                        RunningType.SteppingOver -> "Stepping Over"
                    }
                )

                threadNowRunning = when (timelineEvent.eventAndNextRunningType.nextRunningType) {
                    RunningType.EvaluationAll -> threadHoldingCurrentEvent.treadUiData
                    else -> null
                }
            }
            currentEventIndex++
        }

        setStateText("Done")
    }
}



private fun lineLikeRect(h: Double): RoundRect = RoundRect(Size(lineLikeRectWidth * 2, h), RectCorners(cornerR))
