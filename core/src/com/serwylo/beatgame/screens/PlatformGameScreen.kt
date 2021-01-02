package com.serwylo.beatgame.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.serwylo.beatgame.BeatGame
import com.serwylo.beatgame.Globals
import com.serwylo.beatgame.HUD
import com.serwylo.beatgame.audio.features.Feature
import com.serwylo.beatgame.audio.features.World
import com.serwylo.beatgame.entities.Ground
import com.serwylo.beatgame.entities.Obstacle
import com.serwylo.beatgame.entities.Player
import com.serwylo.beatgame.graphics.calcDensityScaleFactor
import com.serwylo.beatgame.graphics.makeCamera


class PlatformGameScreen(
        private val game: BeatGame,
        private val world: World
) : ScreenAdapter() {

    private val camera = makeCamera(20, 10, calcDensityScaleFactor())
    private lateinit var hud: HUD
    private val obstacles = generateObstacles(world.features)

    private val ground = Ground()
    private lateinit var player: Player

    private var isInitialised = false

    private var atlas: TextureAtlas? = null

    private var state = State.PENDING
    private var startTime = 0f
    private var deathTimeTime = 0f

    enum class State {
        PENDING,
        WARMING_UP,
        PLAYING,
        DEAD,
        FINISHED,
    }

    override fun show() {

        isInitialised = false

        atlas = TextureAtlas(Gdx.files.internal("sprites.atlas"))

        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {

            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.SPACE) {
                    player.performJump()
                    return true
                } else if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                    game.showMenu()
                    return true
                }

                return false
            }

            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                player.performJump()
                return true
            }
        }

        hud = HUD(atlas!!)

        camera.translate(camera.viewportWidth / 4, camera.viewportHeight / 5, 0f)
        camera.update()

        player = Player(Vector2(SCALE_X, 0f), atlas!!)

        Globals.animationTimer = 0f

        isInitialised = true
    }

    override fun hide() {

        world.dispose()

        atlas?.dispose()
        atlas = null

        Gdx.input.inputProcessor = null
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
    }

    override fun render(delta: Float) {
        if (!isInitialised) {
            return
        }

        Globals.animationTimer += delta

        processInput()

        if (state == State.WARMING_UP) {

            if (Globals.animationTimer - startTime > WARM_UP_TIME) {
                state = State.PLAYING
                world.music.play()
            }

            updateEntities(delta)

        } else if (state == State.DEAD) {

            if (Globals.animationTimer - deathTimeTime > DEATH_TIME) {
                world.music.stop()
                game.endGame(player.getScore())
            }

        } else if (state == State.PLAYING) {

            updateEntities(delta)

        }

        renderEntities(delta)
        hud.render(player)
    }

    private fun processInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (state == State.PENDING) {
                state = State.WARMING_UP
                startTime = Globals.animationTimer
            } else if (state == State.PLAYING || state == State.WARMING_UP) {
                player.performJump()
            }
        }
    }

    private fun updateEntities(delta: Float) {

        checkCollisions()

        player.update(delta)

        if (player.getHealth() <= 0 && state != State.DEAD) {

            state = State.DEAD
            deathTimeTime = Globals.animationTimer

        } else {

            camera.translate(delta * SCALE_X, 0f)
            camera.update()

        }

    }

    private fun renderEntities(delta: Float) {
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        ground.render(camera)
        obstacles.forEach { it.render(camera) }
        player.render(camera)
    }

    private fun checkCollisions() {
        obstacles.forEach {
            if (player.isColliding(it.rect)) {
                player.hit(it)
            }
        }
    }

    companion object {

        /**
         * To convert horizontal units from seconds -> metres. That sounds a bit odd, but this is a side
         * scrolling game where features appear at very specific time points, and the screen scrolls
         * at a consistent rate. Therefore it does kind-of-in-an-odd-way make sense to multiple a seconds
         * value to get a horizontal offset in metres.
         *
         * All of the level generation starts with music, which is measured in samples at a particular
         * sample rate.
         *
         * This is then converted into specific time points in seconds, so that regardless of the sample
         * rate of a particular song, all songs produce features of the same duration.
         *
         * The final step is to convert seconds into measurements on screen. This is used for that.
         */
        const val SCALE_X = 5f

        /**
         * Less than this distance between obstacles, and we will merge them together (i.e. increase
         * the size of the one on the left until it reaches the one on the right).
         */
        private const val OBSTACLE_GAP_THRESHOLD = 0.175f

        /**
         * The features extracted from audio line up with exactly when a particular feature of the
         * music is detected. The game is more fun when this lines up with when you'd expect the
         * player to have to jump in order to avoid the feature (more rhythmic that way), so we
         * offset each feature by this many seconds.
         */
        private const val FEATURE_START_TIME_OFFSET = -0.1f

        /**
         * Once the game starts, the player runs infinitely until the player jumps for the first
         * time. After that, wait this long before starting the song.
         */
        private const val WARM_UP_TIME = 3f


        /**
         * How long after dying before we move onto the game end screen.
         * The animation of death is handled differently, managed by the [Player] class.
         */
        private const val DEATH_TIME = 5f

        private fun generateObstacles(features: List<Feature>): List<Obstacle> {
            val rects = features.map {
                Rectangle(
                        (it.startTimeInSeconds + FEATURE_START_TIME_OFFSET + WARM_UP_TIME) * SCALE_X,
                        0f,
                        it.durationInSeconds * SCALE_X,
                        (it.strength * Obstacle.STRENGTH_TO_HEIGHT).coerceAtLeast(Obstacle.MIN_HEIGHT)
                )
            }
            .filter { true } // Exclude tall but narrow items (they will become something else.

            for (i in rects.indices) {
                if (i < rects.size - 1 ) {
                    val distanceToNext = rects[i + 1].x - rects[i].x - rects[i].width
                    if (distanceToNext < OBSTACLE_GAP_THRESHOLD) {
                        rects[i].width += distanceToNext
                    }
                }
            }

            return rects.map { Obstacle(it) }

        }

    }

}