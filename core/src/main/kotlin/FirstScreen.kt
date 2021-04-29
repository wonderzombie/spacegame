import FirstScreen.Kind
import FirstScreen.Kind.ENEMY
import FirstScreen.Kind.MISSILE
import FirstScreen.Kind.PLAYER
import PlayerInputListener.State.IDLE
import PlayerInputListener.State.MOVING
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys.DOWN
import com.badlogic.gdx.Input.Keys.LEFT
import com.badlogic.gdx.Input.Keys.RIGHT
import com.badlogic.gdx.Input.Keys.SPACE
import com.badlogic.gdx.Input.Keys.UP
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Logger
import com.badlogic.gdx.utils.Logger.INFO
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.actors.then
import ktx.assets.Asset
import ktx.collections.GdxArray
import ktx.scene2d.actor
import ktx.scene2d.actors

val logger = Logger("1st", INFO)

/** First screen of the application. Displayed after the application is created.  */
class FirstScreen(
  private val missile: Asset<Texture>,
  private val enemyShip: Asset<Texture>,
  val playerShip: Asset<Texture>
) : Screen {
  enum class Kind {
    UNSET, PLAYER, ENEMY, MISSILE
  }

  private var enemiesDestroyed = 0
  private lateinit var font: BitmapFont
  private lateinit var player: SpaceActor
  private val camera = OrthographicCamera()
  private val viewport = FitViewport(160f, 120f)
  private val stage = Stage(viewport)

  override fun show() {
    Gdx.input.inputProcessor = stage
    font = BitmapFont()

    missile.finishLoading()
    enemyShip.finishLoading()
    playerShip.finishLoading()

    camera.setToOrtho(false, 160f, 120f)
    camera.update()

    // Prepare your screen here.
    stage.actors {
      actor(SpaceActor(playerShip, PLAYER, missile)) {
        player = this
//        stage.addActor(missiles)
      }
      repeat(6) { i ->
        actor(SpaceActor(enemyShip, ENEMY)) {
          setPosition(i * 25f, 100f)
//          enemyGroup.addActor(this)
          addAction(
            Actions.forever(
              Actions.sequence(
                Actions.repeat(200, Actions.moveBy(0.05f, 0f)),
                Actions.repeat(200, Actions.moveBy(-0.05f, 0f))
              )
            )
          )
        }
      }
    }
    stage.addListener(PlayerInputListener(player))
    stage.isDebugAll = true

    check(stage.actors.size > 6)
  }

  override fun render(delta: Float) {
    stage.batch.begin()
    font.draw(
      stage.batch,
      "SCORE: $enemiesDestroyed",
      10f,
      viewport.screenHeight - font.capHeight
    )
    stage.batch.end()

    val enemyRect = Rectangle()
    val missileRect = Rectangle()

//    // yes n^2 b/c n is not much more than 10 :P
//    val maybeHits = stage.actors.filter { enemyActor ->
//      if (enemyActor !is SpaceActor || enemyActor.kind != ENEMY) return
//      enemyRect.set(enemyActor.x, enemyActor.y, enemyActor.width, enemyActor.height)
//      return@filter player.missiles.any { actor ->
//        missileRect.set(actor.x, actor.y, actor.width, actor.height)
//        enemyRect.overlaps(missileRect).also { logger.info("hit? $actor") }
//      }
//
//    }
//
//    maybeHits.onEach { hitActor ->
//      maybeBlowUp(hitActor as SpaceActor)
//      stage.actors.removeValue(hitActor, true)
//    }
//
    with(stage) {
      act(delta)
      draw()
    }
  }

  private fun maybeBlowUp(enemy: SpaceActor?) {
    logger.info("maybeBlowUp")
    enemy ?: return
    if (!enemy.isVisible) return

    enemy.clearActions()
    enemiesDestroyed++
    enemy.addAction(
      Actions.addAction(
        Actions.repeat(
          5,
          Actions.rotateBy(0.3f)
        ).then(
          Actions.alpha(0f, 0.5f, Interpolation.fastSlow)
            .then(Actions.run { enemy.isVisible = false })
        )
      )
    )
  }

  override fun resize(width: Int, height: Int) {
    // Resize your screen here. The parameters represent the new window size.
  }

  override fun pause() {
    // Invoked when your application is paused.
  }

  override fun resume() {
    // Invoked when your application is resumed after pause.
    camera.update()
  }

  override fun hide() {
    // This method is called when another screen replaces this one.
  }

  override fun dispose() {
    // Destroy screen's assets here.
  }
}

class SpaceActor(
  private val texture: Asset<Texture>,
  val kind: Kind,
  private val missile: Asset<Texture>? = null,
  val missiles: GdxArray<SpaceActor> = GdxArray(),
) : Actor() {
  val speed: Float = 3f

  init {
    setBounds(x, y, texture.asset.width.toFloat(), texture.asset.height.toFloat())
  }

  override fun draw(batch: Batch?, parentAlpha: Float) {
    batch?.draw(texture.asset, x, y, width, height)
    super.draw(batch, parentAlpha)
  }

  fun fireMissile(initX: Float, initY: Float): SpaceActor? {
    if (missile == null || missiles.size > 2) return null
    val newMissile: SpaceActor
    stage.actors {
      actor(SpaceActor(texture = missile, kind = MISSILE)) {
        setPosition(initX, initY)
        addAction(Actions.forever(Actions.moveBy(0f, 0.5f)))
      }.also { newMissile = it }
    }
    return newMissile
  }
}

class PlayerInputListener(private val player: SpaceActor) : InputListener() {
  private var state: State = IDLE

  enum class State {
    IDLE,
    MOVING
  }

  private val movementKeys: Set<Int> = setOf(UP, RIGHT, DOWN, LEFT)

  override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
    logger.info("$event - $keycode")
    event ?: return false
    if (player.hasActions()) return false

    when (event.keyCode) {
      UP -> player.addAction(Actions.moveBy(0f, player.speed)).also { this.state = MOVING }
      RIGHT -> player.addAction(Actions.moveBy(player.speed, 0f)).also { this.state = MOVING }
      DOWN -> player.addAction(Actions.moveBy(0f, -player.speed)).also { this.state = MOVING }
      LEFT -> player.addAction(Actions.moveBy(-player.speed, 0f)).also { this.state = MOVING }
      SPACE -> player.fireMissile(player.x + (player.width / 4), player.top)
        .also { player.missiles.add(it) }
    }
    return true
  }

  override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
    event ?: return false
    if (keycode !in movementKeys) return false
    state = IDLE
    player.clearActions()
    return true
  }

}