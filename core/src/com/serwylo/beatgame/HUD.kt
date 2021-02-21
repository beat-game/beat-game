package com.serwylo.beatgame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions.*
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.serwylo.beatgame.graphics.ParticleEffectActor
import com.serwylo.beatgame.levels.Score


class HUD(private val score: Score, sprites: Assets.Sprites, private val particles: Assets.Particles) {

    private val stage = Stage(ExtendViewport(400f, 300f))

    private val padding: Float

    private val font = BitmapFont()

    private val textureHeartFull = TextureRegionDrawable(sprites.heart)
    private val textureHeartHalf = TextureRegionDrawable(sprites.heart_half)
    private val textureHeartEmpty = TextureRegionDrawable(sprites.heart_empty)
    private val textureScore = sprites.score
    private val textureDistance = sprites.right_sign
    private val labelStyle: Label.LabelStyle

    private val heartImages: MutableList<Image> = mutableListOf()

    private val distanceLabel: Label
    private val scoreLabel: Label
    private val healthLabel: Label
    private val bottomWidget: HorizontalGroup
    private val healthWidget: HorizontalGroup

    private val scaleSounds: List<Sound>

    private var previousMultiplier = 1
    private var previousHealth = 100

    init {

        scaleSounds = SCALE_SOUND_FILES.map { Gdx.audio.newSound(Gdx.files.internal("sounds/scales/soundset_vibraphone/${it}")) }

        padding = stage.width / 50

        labelStyle = Label.LabelStyle(font, Color.WHITE)

        healthWidget = HorizontalGroup()
        healthWidget.space(padding / 2)

        healthLabel = Label("", labelStyle)
        healthWidget.addActor(healthLabel)

        val heartContainer = HorizontalGroup()
        for (i in 1..5) {
            val heart = Image(textureHeartFull)
            heartImages.add(heart)
            heartContainer.addActor(heart)
        }
        healthWidget.addActor(heartContainer)

        distanceLabel = Label("", labelStyle)
        scoreLabel = Label("", labelStyle)

        // It would make much more sense to put all of these widgets (and the ones in the top right)
        // in a Table. However doing so makes it nigh-on impossible to use actions to shake and move
        // them. The reason is that any update to the text of a label (which happens regularly)
        // will always invalidate the label and also its parent (the Table), resetting the position
        // of everything in it, no matter how far through a given Action is from animating it.
        bottomWidget = HorizontalGroup()
        bottomWidget.setPosition(padding, padding * 2)
        bottomWidget.space(padding / 2)
        bottomWidget.addActor(Image(textureDistance))
        bottomWidget.addActor(distanceLabel)
        bottomWidget.addActor(Image(textureScore))
        bottomWidget.addActor(scoreLabel)

        stage.addActor(bottomWidget)

        healthWidget.setPosition(stage.width - 150f, stage.height - 20f)
        stage.addActor(healthWidget)

    }

    fun render(delta:Float, health: Int) {

        val distance = (score.distancePercent * 100).toInt().toString() + "%"
        val multiplier = if (score.getMultiplier() <= 1) "" else " x ${score.getMultiplier()}"

        healthLabel.setText(health)
        distanceLabel.setText(distance)
        scoreLabel.setText("${score.getPoints()}$multiplier")

        if (previousHealth != health) {
            val previousNumHalfHearts = previousHealth / 10
            val newNumHalfHearts = health / 10
            previousHealth = health

            if (health <= 0) {

                // Don't shake for the end of game screen, looks a bit jarring if we do.
                healthWidget.clearActions()

            } else if (previousNumHalfHearts != newNumHalfHearts) {

                if (newNumHalfHearts <= 2) {

                    // If there are two half hearts, shake less than one half heart, less so for zero half hearts (almost dead)
                    val shakeDistance = heartImages[0].width / 5f / (newNumHalfHearts + 1)
                    val shakeTime = 0.03f

                    healthWidget.clearActions()
                    healthWidget.addAction(
                            forever(
                                    sequence(
                                            moveBy(shakeDistance / 2, 0f, shakeTime),
                                            moveBy( - shakeDistance, 0f, shakeTime * 2),
                                            moveBy(shakeDistance / 2, shakeTime)
                                    )
                            )
                    )

                }

                for (i in newNumHalfHearts until previousNumHalfHearts) {
                    val imageToOverlay = heartImages[i / 2]
                    val pos = imageToOverlay.parent.localToStageCoordinates(Vector2(imageToOverlay.x, imageToOverlay.y))

                    val pActor = ParticleEffectActor(particles.health)
                    pActor.setPosition(pos.x, pos.y)
                    stage.addActor(pActor)
                }

                heartImages.forEachIndexed { i, image ->
                    val fullThreshold = (i + 1) * 20
                    val halfThreshold = fullThreshold - 10

                    image.drawable = when {
                        health >= fullThreshold -> textureHeartFull
                        health >= halfThreshold -> textureHeartHalf
                        else -> textureHeartEmpty
                    }
                }

            }
        }

        // Bring the increasing multiplier to the players attention by showing
        // a floating up, increasing size, reducing alpha label explaining the new multiplier.
        if (previousMultiplier != score.getMultiplier()) {
            previousMultiplier = score.getMultiplier()

            if (score.getMultiplier() > 1) {
                stage.addActor(createIncreasedMultiplier(score.getMultiplier()))
                playScaleSound(score.getMultiplier())
            }
        }

        stage.act(delta)
        stage.draw()

    }


    /**
     * Play an ever increasing xylophone sound for long combos
     */
    private fun playScaleSound(multiplier: Int) {
        val scaleIndex = multiplier.coerceAtMost(scaleSounds.size - 1)
        val volume = (SCALE_SOUND_VOLUME * scaleIndex).coerceAtMost(1f)
        scaleSounds[scaleIndex].play(volume)
    }

    private fun createIncreasedMultiplier(scoreMultiplier: Int): Actor {
        val label = Container<Label>(Label("x $scoreMultiplier", labelStyle))
        label.isTransform = true

        label.addAction(
                sequence(
                        parallel(
                                alpha(0.3f, 1f),
                                scaleBy(3f, 3f, 1f),
                                moveBy(0f, scoreLabel.height * 4, 1f)
                        ),
                        removeActor()
                )
        )

        // Weird using the score labels height here, but we don't have an exact place to measure this
        // yet, so just sort of guessing. It doesn't really matter, because the animation of this
        // actor makes it hard to see exactly where it started.
        label.x = padding + bottomWidget.prefWidth - scoreLabel.height
        label.y = padding

        return label
    }

    fun dispose() {
        scaleSounds.forEach { it.dispose() }
    }

    companion object {

        private const val SCALE_SOUND_VOLUME = 0.05f

        private val SCALE_SOUND_FILES = listOf(
                "n01.mp3",
                "n02.mp3",
                "n03.mp3",
                "n04.mp3",
                "n05.mp3",
                "n06.mp3",
                "n07.mp3",
                "n08.mp3",
                "n09.mp3",
                "n10.mp3",
                "n11.mp3",
                "n12.mp3",
                "n13.mp3",
                "n14.mp3",
                "n15.mp3",
                "n16.mp3",
                "n17.mp3",
                "n18.mp3",
                "n19.mp3",
                "n20.mp3",
                "n21.mp3",
                "n22.mp3",
                "n23.mp3",
                "n24.mp3"
        )

    }

}