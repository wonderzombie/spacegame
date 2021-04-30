import FirstScreen.Kind
import FirstScreen.Kind.BOMB
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
import com.badlogic.gdx.utils.TimeUtils
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
  private val bomb: Asset<Texture>,
  val enemyShip: Asset<Texture>,
  val playerShip: Asset<Texture>
) : Screen {
  enum class Kind {
    UNSET, PLAYER, ENEMY, MISSILE, BOMB
  }

  private lateinit var font: BitmapFont
  private lateinit var player: SpaceActor

  private var enemiesDestroyed = 0
  private val enemyBombInterval = 2000L
  private var timeSinceLastBomb: Long = enemyBombInterval * 2

  private val camera = OrthographicCamera()
  private val viewport = FitViewport(160f, 120f)
  private val stage = Stage(viewport)

  override fun show() {
    Gdx.input.inputProcessor = stage
    font = BitmapFont()

    missile.finishLoading()
    bomb.finishLoading()
    playerShip.finishLoading()
    enemyShip.finishLoading()

    camera.setToOrtho(false, 160f, 120f)
    camera.update()

    stage.actors {
      actor(SpaceActor(playerShip, PLAYER, missile)) {
        player = this
      }
      repeat(6) { i ->
        actor(SpaceActor(enemyShip, ENEMY)) {
          setPosition(i * 25f, 100f)
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

  private fun Actor.getRect(rect: Rectangle): Rectangle =
    rect.set(this.x, this.y, this.width, this.height)


  override fun render(delta: Float) {
    val enemyRect = Rectangle()
    val missileRect = Rectangle()

    val spaceActors: Map<Kind, List<SpaceActor>> =
      stage.actors.filterIsInstance<SpaceActor>().groupBy { it.kind }

    val collisions = mutableMapOf<SpaceActor, SpaceActor>()
    spaceActors[MISSILE]?.onEach { missile ->
      missileRect.set(missile.x, missile.y, missile.width, missile.height)
      spaceActors[ENEMY]?.onEach { enemy ->
        enemyRect.set(enemy.x, enemy.y, enemy.width, enemy.height)
        if (enemyRect.overlaps(missileRect)) collisions += missile to enemy
      }
    }

    collisions.onEach { (projectile, target) ->
      projectile.isVisible = false
      maybeBlowUp(target)
      stage.actors.removeValue(projectile, true)
      stage.actors.removeValue(target, true)
      player.missiles.removeValue(projectile, true)
    }

    spaceActors[MISSILE].orEmpty().onEach {
      if (it.y > stage.height) {
        stage.actors.removeValue(it, true)
        player.missiles.removeValue(it, true)
        logger.info("removed $it ${it.kind}")
      }
    }

    if (timeSinceLastBomb >= enemyBombInterval) {
      stage.actors.filterIsInstance<SpaceActor>().random().apply {
        dropBomb(x + (width / 2), y)
      }
    }
    timeSinceLastBomb = TimeUtils.millis()

    with(stage) {
      act(delta)
      draw()
    }

    stage.batch.begin()
    font.draw(
      stage.batch,
      "SCORE: $enemiesDestroyed",
      10f,
      50f
    )
    stage.batch.end()
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
          Actions.rotateBy(5f)
        ).then(
          Actions.alpha(0f, 0.5f, Interpolation.fastSlow)
            .then(Actions.run { enemy.remove() })
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
  private val bomb: Asset<Texture>? = null,
  val bombs: GdxArray<SpaceActor> = GdxArray()
) : Actor() {
  val speed: Float = 3f

  init {
    setBounds(x, y, texture.asset.width.toFloat(), texture.asset.height.toFloat())
  }

  override fun draw(batch: Batch?, parentAlpha: Float) {
    batch?.draw(texture.asset, x, y, width, height)
    super.draw(batch, parentAlpha)
  }

  fun dropBomb(initX: Float, initY: Float): SpaceActor? {
    if (bomb == null || bombs.size > 0) return null
    val newBomb: SpaceActor
    stage.actors {
      actor(SpaceActor(texture = bomb, kind = BOMB)) {
        setPosition(initX, initY)
        addAction(Actions.forever(Actions.moveBy(0f, -0.5f)))
      }.also { newBomb = it }
    }
    return newBomb

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
    return newMissile.also { missiles.add(it) }
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