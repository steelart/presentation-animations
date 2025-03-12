fun coroutineCase(): List<TreadUiData> {
    val getCoroutineInjection = FrameExecution("getCoroutineId", buildList {
        selfExecutionArea(shortExecutionLen)
    }, frameType = FrameType.Evaluation)


    val execution1 = FrameExecution("dispatch", buildList {
        selfExecutionArea(shortExecutionLen)
        frameExecution("launch 1", frameType = FrameType.CoroutineBorder("Coroutine#1", true, false)) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("fff") {
                selfExecutionArea(shortExecutionLen)
            }
        }

        selfExecutionArea(shortExecutionLen)

        frameExecution("launch 3", frameType = FrameType.CoroutineBorder("Coroutine#3", true, false)) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("hhh") {
                selfExecutionArea(shortExecutionLen)
            }
        }

        selfExecutionArea(longExecutionLen*1.5)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#1", false, false)) {
            frameExecution("fff") {
                selfExecutionArea(shortExecutionLen)
            }
            selfExecutionArea(shortExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.SuspendAllBreakpoint, RunningType.SteppingOverThread("func()"), getCoroutineInjection))
                selfExecutionArea(shortExecutionLen, TimelineEventType.SetFilterEvent("Coroutine#1"), RunningType.ResumeAll)
                frameExecution("func") {
                    selfExecutionArea(shortExecutionLen)
                }
                selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendThreadPause, RunningType.ResumeAll)
            }
        }

        selfExecutionArea(longExecutionLen)

        frameExecution("launch 5", frameType = FrameType.CoroutineBorder("Coroutine#5", true, false)) {
            selfExecutionArea(longExecutionLen)

            for (i in 0..4) {
                frameExecution("hhh") {
                    selfExecutionArea(longExecutionLen)
                }
                selfExecutionArea(shortExecutionLen)
            }
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(
                    TimelineEventType.TechnicalThreadBreakpoint, RunningType.ResumeAll, getCoroutineInjection,
                    //≠
                    EventAndNextRunningType(TimelineEventType.ConditionCheck("Coroutine#5 != Coroutine#1"), RunningType.ResumeAll)
                ))
            }
            selfExecutionArea(longExecutionLen)
        }

        selfExecutionArea(longExecutionLen*1.5)

        for (i in 50..103) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("launch $i", frameType = FrameType.CoroutineBorder("Coroutine#$i", true, false)) {
                selfExecutionArea(longExecutionLen)
            }
        }
    })

    val execution2 = FrameExecution("dispatch", buildList {
        selfExecutionArea(shortExecutionLen*1.5)

        frameExecution("launch 2", frameType = FrameType.CoroutineBorder("Coroutine#2", true, false)) {
            selfExecutionArea(longExecutionLen)
            frameExecution("ggg") {
                selfExecutionArea(shortExecutionLen)
            }
        }

        selfExecutionArea(shortExecutionLen)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#1", false, false)) {
            frameExecution("fff") {
                selfExecutionArea(longExecutionLen)
            }
        }

        selfExecutionArea(longExecutionLen)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#3", false, true)) {
            frameExecution("hhh") {
                selfExecutionArea(longExecutionLen)
            }
            selfExecutionArea(longExecutionLen)
        }

        selfExecutionArea(longExecutionLen)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#4", true, false)) {
            selfExecutionArea(longExecutionLen)
            frameExecution("func") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(
                    TimelineEventType.TechnicalThreadBreakpoint, RunningType.ResumeAll, getCoroutineInjection,
                    EventAndNextRunningType(TimelineEventType.ConditionCheck("Coroutine#4 != Coroutine#1"), RunningType.ResumeAll)
                ))
                selfExecutionArea(longExecutionLen)
            }
            selfExecutionArea(longExecutionLen)
        }

        for (i in 0..2) {
            selfExecutionArea(longExecutionLen)

            frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#2", false, false)) {
                selfExecutionArea(longExecutionLen)
                frameExecution("ggg") {
                    selfExecutionArea(shortExecutionLen)
                }
            }
        }

        selfExecutionArea(longExecutionLen)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#1", false, true)) {
            frameExecution("") {
                frameExecution("func") {
                    selfExecutionArea(shortExecutionLen, EventAndNextRunningType(
                        TimelineEventType.TechnicalThreadBreakpoint, RunningType.ResumeAll, getCoroutineInjection,
                        EventAndNextRunningType(TimelineEventType.ConditionCheck("Coroutine#1 == Coroutine#1"), RunningType.ResumeAll)
                    ))
                    repeat(3) {
                        selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendThreadPause, RunningType.ResumeAll)
                    }
                    selfExecutionArea(shortExecutionLen, TimelineEventType.SuspendAllSteppingPermanentEnd, RunningType.ExecutionEnd)
                    selfExecutionArea(longExecutionLen)
                }
            }
            selfExecutionArea(longExecutionLen)
        }
    })
    return listOf(
        TreadUiData(windowSize.height/6, execution1, "worker-1"),
        TreadUiData(windowSize.height/6 + threadHeight*2, execution2, "worker-2"),
    )
}
