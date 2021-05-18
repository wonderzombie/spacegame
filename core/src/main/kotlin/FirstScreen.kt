import FirstScreen.GameState.GAME_OVER
import FirstScreen.Kind.BOMB
import FirstScreen.Kind.ENEMY
import FirstScreen.Kind.MISSILE
import FirstScreen.Kind.PLAYER
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Logger
import com.badlogic.gdx.utils.Logger.INFO
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import ktx.actors.then
import ktx.assets.Asset
import ktx.collections.GdxArray
import ktx.graphics.use
import ktx.scene2d.actor
import ktx.scene2d.actors

val logger = Logger("1st", INFO)

/** First screen of the application. Displayed after the application is created.  */
class FirstScreen(
  private val font: Asset<BitmapFont>,
  private val playerShipTex: Asset<Texture>,
  private val enemyShipTex: Asset<Texture>,
  private val missileTex: Asset<Texture>,
  private val bombTex: Asset<Texture>,
) : Screen {
  enum class Kind {
    PLAYER, ENEMY, MISSILE, BOMB;

    companion object {
      val ProjectileKinds = listOf(MISSILE, BOMB)
      val ActorKinds = listOf(PLAYER, ENEMY)
    }
  }

  private lateinit var gameState: GameState
  private lateinit var player: SpaceActor

  private val camera = OrthographicCamera()
  private val viewport = ExtendViewport(320f, 240f, camera)
  private val stage = Stage(viewport)

  private var enemyBombs: GdxArray<SpaceActor> = GdxArray()

  private val defaultLives: Int = 5
  private val enemyBombInterval: Long = 5 * 1000

  private var remainingLives: Int = defaultLives
  private var currentLevel: Int = 1
  private var enemiesDestroyed: Int = 0
  private var lastBombTime: Long = enemyBombInterval * 2


  override fun show() {
    Gdx.input.inputProcessor = stage

    missileTex.finishLoading()
    bombTex.finishLoading()
    playerShipTex.finishLoading()
    enemyShipTex.finishLoading()

    camera.setToOrtho(false, viewport.worldWidth, viewport.worldHeight)
    camera.update()

    stage.actors {
      actor(SpaceActor(playerShipTex, PLAYER, missileTex)).also { player = it }
      repeat(6) { i ->
        actor(SpaceActor(enemyShipTex, ENEMY, bomb = bombTex)) {
          setPosition(i * 25f, 100f)
          addAction(enemyPatrol())
        }
      }
    }
    stage.addListener(PlayerInputListener(player))
    stage.isDebugAll = true

    check(stage.actors.size > 6)
  }

  private fun enemyPatrol() = Actions.forever(
    Actions.sequence(
      Actions.repeat(200, Actions.moveBy(0.05f, 0f)),
      Actions.repeat(200, Actions.moveBy(-0.05f, 0f))
    )
  )

  private fun Actor.getRect(rect: Rectangle): Rectangle =
    rect.set(this.x, this.y, this.width, this.height)


  override fun render(delta: Float) {
    val actorRect = Rectangle()
    val projectleRect = Rectangle()

    val spaceActors: Map<Kind, List<SpaceActor>> =
      stage.actors.filterIsInstance<SpaceActor>().groupBy { it.kind }

    val collisions = mutableMapOf<SpaceActor, SpaceActor>()
    spaceActors[MISSILE]?.onEach { missile ->
      projectleRect.set(missile.x, missile.y, missile.width, missile.height)
      spaceActors[ENEMY]?.onEach { enemy ->
        actorRect.set(enemy.x, enemy.y, enemy.width, enemy.height)
        if (actorRect.overlaps(projectleRect)) collisions += missile to enemy
      }
    }

    spaceActors[BOMB]?.onEach { bomb ->
      bomb.getRect(projectleRect)
      if (projectleRect.overlaps(player.getRect(actorRect))) {
        handlePlayerHit(player, bomb)
      }
    }

    collisions.onEach { (projectile, target) ->
      projectile.isVisible = false
      maybeBlowUp(target)
      stage.actors.removeValue(projectile, true)
      stage.actors.removeValue(target, true)
      player.missiles.removeValue(projectile, true)
    }

    cleanupProjectiles()

    lastBombTime = maybeDropBomb(lastBombTime)

    with(stage) {
      act(delta)
      draw()
    }

    stage.batch.use {
      drawHud(it)
    }
  }

  private fun drawHud(batch: Batch) {
    font.asset.draw(
      batch,
      "SCORE: $enemiesDestroyed",
      5f,
      130f
    )
    font.asset.draw(
      batch,
      "LEVEL $currentLevel",
      60f,
      130f
    )
    font.asset.draw(
      batch,
      "LIVES: $remainingLives",
      120f,
      130f
    )
  }

  private fun handlePlayerHit(player: SpaceActor, bomb: SpaceActor) {
    enemyBombs.removeValue(bomb, true)
    stage.actors.removeValue(bomb, true)

    remainingLives -= 1
    player.clearActions()
    player.addAction(playerHit())

    if (remainingLives == 0) {
      handlePlayerLoss(player)
    }
  }

  private fun playerHit() = Actions.repeat(
    5,
    Actions.sequence(Actions.color(Color.RED, 1f), Actions.color(Color.WHITE, 1f))
  )

  enum class GameState {
    RUNNING,
    NEW_LEVEL,
    GAME_OVER
  }

  private fun handlePlayerLoss(player: SpaceActor) {
    player.isVisible = false
    gameState = GAME_OVER
    currentLevel = 1
  }

  private fun maybeDropBomb(lastBombTime: Long): Long {
    if (TimeUtils.timeSinceMillis(lastBombTime) >= enemyBombInterval) {
      stage.actors.filterIsInstance<SpaceActor>().filter { it.kind == ENEMY }.random().apply {
        dropBomb(x + (width / 2), y)?.let { enemyBombs.add(it) }
      }
      return TimeUtils.millis()
    }
    return lastBombTime
  }

  private fun cleanupProjectiles() {
    val projectiles: List<SpaceActor> = stage.actors.filterIsInstance<SpaceActor>()
      .filter { it.kind in Kind.ProjectileKinds }

    projectiles.onEach {
      if (it.y > stage.height) {
        stage.actors.removeValue(it, true)
        when (it.kind) {
          MISSILE -> player.missiles.removeValue(it, true)
          BOMB -> enemyBombs.removeValue(it, true)
        }
        logger.info("removed $it ${it.kind}")
      }
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

