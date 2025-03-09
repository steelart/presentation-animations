
fun simpleOnThreadCase(): List<TreadUiData> {
    val execution = FrameExecution("main", buildList {
        selfExecutionArea(longExecutionLen)
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen)
            selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllBreakpoint, RunningType.SteppingOver("boo"))
            frameExecution("boo") {
                selfExecutionArea(longExecutionLen)
            }
            selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllSteppingEnd, RunningType.SteppingOver("bar"))
            frameExecution("bar") {
                selfExecutionArea(longExecutionLen, TimelineEventType.SuspendAllBreakpoint, RunningType.Running)
            }
        }
        selfExecutionArea(shortExecutionLen)
    })

    return listOf(TreadUiData(windowSize.height/4, execution, "main-thread"))
}

fun twoThreadsCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllBreakpoint, RunningType.SteppingOver("foo"))
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen)
        }
        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllSteppingEnd, RunningType.Running)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("another", buildList {
        selfExecutionArea(shortExecutionLen)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })
    return listOf(
        TreadUiData(windowSize.height/8, execution1, "Thread-1"),
        TreadUiData(windowSize.height/8 + threadHeight*2, execution2, "Thread-2"),
    )
}

fun breakpointInAnotherThreadCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllBreakpoint, RunningType.SteppingOver("foo"))
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen*3)
        }
        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllSteppingEnd, RunningType.Running)
        for (i in 0..10) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("another", buildList {
        selfExecutionArea(longExecutionLen)

        frameExecution("some") {
            selfExecutionArea(longExecutionLen)
            selfExecutionArea(longExecutionLen, TimelineEventType.SkippedBreakpoint, RunningType.SteppingOver("some"))
        }

        for (i in 0..100) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen)
            }
        }
    })
    return listOf(
        TreadUiData(windowSize.height/8, execution1, "Thread-1"),
        TreadUiData(windowSize.height/8 + threadHeight*2, execution2, "Thread-2"),
    )
}

fun suspendThreadModeCase(): List<TreadUiData> {
    val execution1 = FrameExecution("bar", buildList {
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendThreadPermanentBreakpoint, RunningType.SteppingOver("foo"))
        frameExecution("foo") {
            selfExecutionArea(longExecutionLen*3)
        }
        selfExecutionArea(longExecutionLen)
        selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        selfExecutionArea(longExecutionLen)
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
            selfExecutionArea(longExecutionLen, TimelineEventType.SuspendThreadPermanentBreakpoint, RunningType.Running)
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
        TreadUiData(windowSize.height/4, execution1, "Thread-1"),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2, "Thread-2"),
        TreadUiData(windowSize.height/4 + threadHeight*4, execution3, "Thread-3"),
    )
}

