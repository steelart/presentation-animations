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
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.Breakpoint, RunningType.ResumeThread, getCoroutineInjection))
                selfExecutionArea(shortExecutionLen, TimelineEventType.SetFilterEvent("Coroutine#1"), RunningType.SteppingOver("func()"))
                frameExecution("func") {
                    selfExecutionArea(shortExecutionLen)
                }
            }
            //selfExecutionArea(shortExecutionLen, TimelineEventType.SteppingEnd, RunningType.Running)
        }

        for (i in 50..53) {
            selfExecutionArea(shortExecutionLen)
            frameExecution("launch $i") {
                selfExecutionArea(longExecutionLen)
            }
        }
        frameExecution("launch 56") {
            selfExecutionArea(longExecutionLen)
            frameExecution("boo") {
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.BreakpointTmpThread, RunningType.ResumeThread, getCoroutineInjection))
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
            frameExecution("someAnotherFunc") {
                selfExecutionArea(longExecutionLen)
                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.Breakpoint, RunningType.ResumeThread, getCoroutineInjection))
                selfExecutionArea(longExecutionLen)
            }
        }

        selfExecutionArea(longExecutionLen)

        frameExecution("", frameType = FrameType.CoroutineBorder("Coroutine#4", false, true)) {
            frameExecution("someAnotherFunc") {
                selfExecutionArea(longExecutionLen)
//                selfExecutionArea(shortExecutionLen, EventAndNextRunningType(TimelineEventType.Breakpoint, RunningType.EvaluationThread, getCoroutineInjection))
//                selfExecutionArea(longExecutionLen)
            }
            selfExecutionArea(longExecutionLen)
        }
    })
    return listOf(
        TreadUiData(windowSize.height/4, execution1),
        TreadUiData(windowSize.height/4 + threadHeight*2, execution2),
    )
}
