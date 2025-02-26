import korlibs.time.*
import korlibs.korge.*
import korlibs.korge.scene.*
import korlibs.korge.tween.*
import korlibs.korge.view.*
import korlibs.image.color.*
import korlibs.image.format.*
import korlibs.io.file.std.*
import korlibs.math.geom.*
import korlibs.math.interpolation.*


val windowSize = Size(512, 512)
const val threadHeight = 100.0


suspend fun main() = Korge(windowSize = windowSize, backgroundColor = Colors["#2b2b2b"]) {
    val sceneContainer = sceneContainer()


    sceneContainer.changeTo { MyScene() }
}


class MyScene : Scene() {
    override suspend fun SContainer.sceneMain() {
        val firstThreadY = 100.0

        solidRect(windowSize.width, threadHeight, Colors.BLUEVIOLET) {
            y = firstThreadY
        }


        val chunkWidth = 200.0
        val chunk = roundRect(Size(chunkWidth, threadHeight), RectCorners(5.0), fill = Colors.ORANGE)
        chunk.y = firstThreadY
        chunk.tween(chunk::x[windowSize.width, 0.0 - chunkWidth], time = 5.seconds, easing = Easing.LINEAR)


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
